package handlers;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Serializer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String serialize(Object objet) {
        try {
            return mapper.writeValueAsString(objet);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
}
