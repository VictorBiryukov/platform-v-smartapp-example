package handlers;

import com.cloud.apigateway.sdk.utils.Client;
import com.cloud.apigateway.sdk.utils.Request;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import ru.sber.platformv.faas.api.HttpFunction;
import ru.sber.platformv.faas.api.HttpRequest;
import ru.sber.platformv.faas.api.HttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Log
public class Handler implements HttpFunction {

    public static final String BATCH_SIZE_REQUEST_PARAM = "batchSize";
    private static final String WORD_REGEXP = "[а-яёА-ЯЁ]+";
    private static final java.util.regex.Pattern WORD_PATTERN = java.util.regex.Pattern.compile(WORD_REGEXP);
    private final ExecutorService dictionaryTaskExecutor = Executors.newFixedThreadPool(10);
    private final String appKey = System.getenv("APP_KEY");
    private final String appSecret = System.getenv("APP_SECRET");
    private final String dataSpaceUrl = System.getenv("DATASPACE_URL");

    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        if (request.getMethod().equals("GET") && request.getPath().equals("/metrics")) {
            response.setStatusCode(404, "Bad request");
            response.getWriter().write("No endpoint match the request");
            return;
        }

        int batchSize = Integer.parseInt(request.getFirstQueryParameter(BATCH_SIZE_REQUEST_PARAM).get());

        final JsonPointer[] createdJsonPointers = prepareCreatedJsonPointers(batchSize);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> currentBatch = new ArrayList<>();
            final List<BatchTask> batchTasks = new ArrayList<>();
            final StringBuilder errorReportBuilder = new StringBuilder();

            String currentLine = reader.readLine();

            while (currentLine != null) {
                if (WORD_PATTERN.matcher(currentLine).matches()) {
                    currentBatch.add(currentLine.toLowerCase());
                    if (currentBatch.size() == batchSize) {
                        batchTasks.add(executeBatchAsync(currentBatch, createdJsonPointers));
                        currentBatch = new ArrayList<>();
                    }
                } else {
                    log.warning(currentLine + " was skipped because of containing unsupported characters");
                }
                currentLine = reader.readLine();
            }

            if (!currentBatch.isEmpty()) {
                batchTasks.add(executeBatchAsync(currentBatch, createdJsonPointers));
            }

            for (final BatchTask batchTask : batchTasks) {
                checkFutureAndAppendErrorIfNeeded(batchTask, errorReportBuilder);
            }

            log.info("Applying of words has been finished");

            if (errorReportBuilder.length() == 0) {
                response.setContentType("application/json; charset=utf-8");
                response.setStatusCode(200);
                response.getWriter().write(Response.success().serialize());
            } else {
                response.setContentType("application/json; charset=utf-8");
                response.setStatusCode(500);
                response.getWriter().write(Response.error(errorReportBuilder.toString()).serialize());

            }
        } catch (final Exception e) {
            log.warning("Problem during applyDictionary: " + e.getMessage());
            response.setContentType("application/json; charset=utf-8");
            response.setStatusCode(500);
            response.getWriter().write(Response.error("Unexpected error").serialize());
        }
    }


    private JsonPointer[] prepareCreatedJsonPointers(final int batchSize) {
        final JsonPointer[] pointers = new JsonPointer[batchSize];
        for (int i = 0; i < batchSize; i++) {
            pointers[i] = JsonPointer.compile("/data/p" + i + "/updateOrCreateWord/created");
        }
        return pointers;
    }

    private BatchTask executeBatchAsync(final List<String> batch, final JsonPointer[] createdJsonPointers) {
        try {
            return new BatchTask(batch, dictionaryTaskExecutor.submit(() -> applyBatch(batch, createdJsonPointers)));
        } catch (final Exception e) {
            log.warning("Cannot insert or update words from " + batch.get(0) + " to " + batch.get(batch.size() - 1) + ". " + e);

            return new BatchTask(batch, null);
        }
    }

    private void applyBatch(final List<String> batch, final JsonPointer[] createdJsonPointers) {
        try {
            final StringBuilder sb =
                    new StringBuilder("{\"operationName\":null,\"variables\":{},\"query\":\"mutation {\n");
            for (int i = 0; i < batch.size(); i++) {
                sb.append(getPacketSubMutation(i, batch.get(i)));
            }
            sb.append("}\n\"}");

            final JsonNode jsonNode = callDataSpace(sb.toString());
            for (int i = 0; i < batch.size(); i++) {
                //noinspection ConstantConditions
                final JsonNode booleanNode = jsonNode.at(createdJsonPointers[i]);
                if (!booleanNode.isBoolean()) {
                    throw new IllegalStateException("Value at " + createdJsonPointers[i] + " is not boolean");
                }
            }
        } catch (final Exception e) {
            log.warning("Cannot insert or update words from " + batch.get(0) + " to " + batch.get(batch.size() - 1) + ". " + e);

            throw new IllegalStateException("Cannot insert or update words");
        }
    }

    private void checkFutureAndAppendErrorIfNeeded(final BatchTask batchTask, final StringBuilder stringBuilder)
            throws InterruptedException {
        final Future<?> future = batchTask.getFuture();
        if (future == null || future.isCancelled()) {
            stringBuilder.append(batchTask.getErrorString()).append("\n");
        } else {
            try {
                future.get();
                log.warning(batchTask.getOkString());
            } catch (final ExecutionException e) {
                stringBuilder.append(batchTask.getErrorString()).append("\n");
            }
        }
    }

    private String getPacketSubMutation(final int packetIndex, final String word) {
        //noinspection GrazieInspection
        return "  p" + packetIndex + ": packet {\n" +
               "    updateOrCreateWord(input: {data: \\\"" + word + "\\\", length: " + word.length() + "}, exist: {byKey: data}) {\n" +
               "      created\n" +
               "    }\n" +
               "  }\n";
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