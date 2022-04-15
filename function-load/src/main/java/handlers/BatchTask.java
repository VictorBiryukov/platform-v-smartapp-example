package handlers;

import lombok.Value;

import java.util.List;
import java.util.concurrent.Future;

@Value
public class BatchTask {

    List<String> batch;
    Future<?> future;

    public String getOkString() {
        return "Words from " + batch.get(0) + " to " + batch.get(batch.size() - 1) + " has been processed";
    }

    public String getErrorString() {
        return "Cannot process words from " + batch.get(0) + " to " + batch.get(batch.size() - 1);
    }

}
