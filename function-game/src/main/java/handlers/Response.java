package handlers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(Include.NON_EMPTY)
public class Response implements Serializable {

    Status status;
    String word;
    String description;

    public static Response success() {
        return new Response(Status.SUCCESS, null, null);
    }

    public static Response success(final String word) {
        return new Response(Status.SUCCESS, word, null);
    }

    public static Response notFound() {
        return new Response(Status.NOT_FOUND, null, null);
    }

    public static Response notASubword() {
        return new Response(Status.NOT_A_SUBWORD, null, null);
    }

    public static Response error(final String description) {
        return new Response(Status.ERROR, null, description);
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public enum Status {

        SUCCESS("success"),
        NOT_FOUND("not found"),
        NOT_A_SUBWORD("not a subword"),
        ERROR("error");

        String representation;

        @JsonValue
        public String getRepresentation() {
            return representation;
        }

    }

}
