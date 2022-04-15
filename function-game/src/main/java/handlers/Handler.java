package handlers;

import com.cloud.apigateway.sdk.utils.Client;
import com.cloud.apigateway.sdk.utils.Request;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import ru.sber.platformv.faas.api.HttpFunction;
import ru.sber.platformv.faas.api.HttpRequest;
import ru.sber.platformv.faas.api.HttpResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

public class Handler implements HttpFunction {

    private final Logger logger = Logger.getLogger(HttpFunction.class.getName());

    public static final String ROOT_PATH = "/anagramDemo";
    public static final String GET_WORD_BY_LENGTH_PATH = ROOT_PATH + "/getWordByLength";
    public static final String CHECK_SUB_WORD_PATH = ROOT_PATH + "/checkSubWord";

    public static final String LENGTH_REQUEST_PARAM = "length";
    public static final String WORD_REQUEST_PARAM = "word";
    public static final String CANDIDATE_REQUEST_PARAM = "candidate";

    private static final String WORD_REGEXP = "[а-яёА-ЯЁ]+";
    private static final int NUMBER_OF_SYMBOLS = 33;
    private static final int MAX_SYMBOL_INDEX = NUMBER_OF_SYMBOLS - 1;

    private static final JsonPointer COUNT_JSON_PTR = JsonPointer.compile("/data/searchWord/count");
    private static final JsonPointer WORD_JSON_PTR = JsonPointer.compile("/data/searchWord/elems/0/data");

    private final String appKey = System.getenv("APP_KEY");
    private final String appSecret = System.getenv("APP_SECRET");
    private final String dataSpaceUrl = System.getenv("DATASPACE_URL");

    private final Random random = new Random();

    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        response.setContentType("application/json; charset=utf-8");
        if ("GET".equalsIgnoreCase(request.getMethod()) && GET_WORD_BY_LENGTH_PATH.equals(request.getPath())) {
            var resp = getWordByLength(getLength(request));
            response.getWriter().write(Serializer.serialize(resp));
        } else if ("GET".equalsIgnoreCase(request.getMethod()) && CHECK_SUB_WORD_PATH.equals(request.getPath())) {
            String word = extractQueryParam(request, WORD_REQUEST_PARAM, true);
            assert word.matches(WORD_REGEXP);
            String candidate = extractQueryParam(request, CANDIDATE_REQUEST_PARAM, true);
            assert candidate.matches(WORD_REGEXP);
            var resp = checkSubWord(word, candidate);
            response.getWriter().write(Serializer.serialize(resp));
        } else {
            response.setStatusCode(404, "Bad request");
            response.getWriter().write("No endpoint match the request");
        }
    }

    private Response checkSubWord(String word, String candidate) {
        final String lowerCaseWord = word.toLowerCase();
        final String lowerCaseCandidate = candidate.toLowerCase();

        if (lowerCaseWord.equals(lowerCaseCandidate)) {
            throw new IllegalArgumentException("word and candidate should be different");
        }

        if (areWordsExist(lowerCaseWord, lowerCaseCandidate)) {
            return isSubWord(lowerCaseWord, lowerCaseCandidate) ? Response.success() : Response.notASubword();
        }

        return Response.notFound();
    }

    private boolean areWordsExist(final String word, final String candidate) {
        try {
            final String query = "{\"operationName\":null,\"variables\":{},\"query\":\"{\n" +
                                 "  searchWord(cond: \\\"it.data=='" + word + "' || it.data=='" + candidate + "'\\\") {\n" +
                                 "    count\n" +
                                 "  }\n" +
                                 "}\"}";
            //noinspection ConstantConditions
            final JsonNode countNode = callDataSpace(query).at(COUNT_JSON_PTR);

            if (countNode.isInt()) {
                return countNode.intValue() == 2;
            }

            throw new IllegalStateException("Value at " + COUNT_JSON_PTR + " is not integer");
        } catch (final Exception e) {
            logger.warning("Cannot check existence of words " + word + "and " + candidate + "\n" + e.getMessage());
            throw new IllegalStateException("Cannot check existence of words");
        }
    }

    private boolean isSubWord(final String word, final String candidate) {
        final int[] wordIndex = new int[NUMBER_OF_SYMBOLS];

        for (final char ch : word.toCharArray()) {
            wordIndex[getCharIndex(ch)]++;
        }

        for (final char ch : candidate.toCharArray()) {
            if (--wordIndex[getCharIndex(ch)] < 0) {
                return false;
            }
        }

        return true;
    }

    private int getCharIndex(final char ch) {
        // для всех символов кроме ё имеем 0 <= ch-'а' < 32, для ё имеем 'ё'-'а'==33; перетаскиваем ё в 32 индекс
        return Math.min(ch - 'а', MAX_SYMBOL_INDEX);
    }

    private String extractQueryParam(HttpRequest request, String name, boolean required) {
        var values = request.getQueryParameters().get(name);
        if (required) {
            if (Objects.isNull(values) || values.isEmpty()) {
                throw new RuntimeException("Request param " + name + " must be provided");
            } else {
                return values.get(0);
            }
        } else {
            if (Objects.isNull(values)) {
                return "";
            } else {
                return values.get(0);
            }
        }
    }

    private Response getWordByLength(int length) {
        final int numberOfWordsWithGivenLength = getNumberOfWordsWithGivenLength(length);

        if (numberOfWordsWithGivenLength > 0) {
            final String randomWordWithGivenLength = getRandomWordWithGivenLength(length, numberOfWordsWithGivenLength);

            return Response.success(randomWordWithGivenLength);
        }

        return Response.notFound();
    }

    private int getNumberOfWordsWithGivenLength(final int length) {
        try {
            final String query = "{\"operationName\":null,\"variables\":{},\"query\":\"{\n" +
                                 "  searchWord(cond: \\\"it.length==" + length + "\\\") {\n" +
                                 "    count\n" +
                                 "  }\n" +
                                 "}\"}";
            //noinspection ConstantConditions
            final JsonNode countNode = callDataSpace(query).at(COUNT_JSON_PTR);

            if (countNode.isInt()) {
                return countNode.intValue();
            }

            throw new IllegalStateException("Value at " + COUNT_JSON_PTR + " is not integer");
        } catch (final Exception e) {
            logger.warning("Cannot get number of words with length " + length + "\n" + e.getMessage());
            throw new IllegalStateException("Cannot get number of words with given length");
        }
    }

    private String getRandomWordWithGivenLength(final int length, final int numberOfWordsWithGivenLength) {
        try {
            final int offset = random.nextInt(numberOfWordsWithGivenLength);
            final String query = "{\"operationName\":null,\"variables\":{},\"query\":\"{\n" +
                                 "  searchWord(cond: \\\"it.length==" + length + "\\\", limit: 1, offset: " + offset + ", sort: {crit: \\\"it.data\\\"}) {\n" +
                                 "    elems {\n" +
                                 "      data\n" +
                                 "    }" +
                                 "  }\n" +
                                 "}\"}";
            //noinspection ConstantConditions
            final JsonNode wordNode = callDataSpace(query).at(WORD_JSON_PTR);

            if (wordNode.isTextual()) {
                return wordNode.textValue();
            }

            throw new IllegalStateException("Value at " + COUNT_JSON_PTR + " is not string");
        } catch (final Exception e) {
            logger.warning("Cannot get random word with length " + length + e.getMessage());
            throw new IllegalStateException("Cannot get random word with given length");
        }
    }

    private int getLength(HttpRequest request) {
        var lengths = request.getQueryParameters().get(LENGTH_REQUEST_PARAM);
        int length = 0;
        if (Objects.nonNull(lengths)) {
            length = Integer.parseInt(lengths.get(0));
            if (length < 2) {
                throw new RuntimeException("length can't be less than 2");
            }
            return length;
        } else {
            throw new RuntimeException("length not provided");
        }
    }

    private JsonNode callDataSpace(final String query) throws Exception {
        final Request request = new Request();
        request.setBody(query);
        request.setMethod("POST");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept", "*/*");
        request.addHeader("Accept-Encoding", "gzip, deflate, br");
        request.addHeader("Connection", "keep-alive");
        request.setKey(appKey);
        request.setSecret(appSecret);
        request.setUrl(dataSpaceUrl);

        CloseableHttpClient client = null;
        try {
            HttpRequestBase signedRequest = Client.sign(request);
            client = HttpClients.custom().build();
            org.apache.http.HttpResponse response = client.execute(signedRequest);
            HttpEntity resEntity = response.getEntity();
            var responseString = EntityUtils.toString(resEntity, "UTF-8");
            return new ObjectMapper().readTree(responseString);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}