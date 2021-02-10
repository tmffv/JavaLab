package tmfv.manager;

import ru.spbstu.pipeline.BaseGrammar;
import ru.spbstu.pipeline.IConfigurable;
import ru.spbstu.pipeline.RC;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Manager implements IConfigurable {
    private static class CheckParamsException extends RuntimeException {
        CheckParamsException(String msg) {
            super(msg);
        }

        String cause() {
            return super.getMessage();
        }
    }

    private static enum Parameters {
        EXECUTOR_NAME,
        READER_NAME,
        WRITER_NAME,
        READER_CONFIG_PATH,
        WRITER_CONFIG_PATH,
        EXECUTOR_CONFIG_PATH,
        INPUT_FILE_PATH,
        OUTPUT_FILE_PATH;

        static String[] all() {
            return (String[]) Arrays.stream(Parameters.values()).map((Enum::name)).toArray();
        }
    }

    private Logger logger;
    private Map<String, String> params;
    private BaseGrammar managerGrammar = new BaseGrammar(Parameters.all()) {
        @Override
        public String delimiter() {
            return super.delimiter();
        }
    };

    @Override
    public RC setConfig(String s) {
        try {
            params = getParams(s);
            checkParams();
        } catch (CheckParamsException e) {
            logWarning(e.cause());
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        } catch (Exception e) {
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }
        
        return RC.CODE_SUCCESS;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    RC start() {
        return null;
    }

    private Map<String, String> getParams(String filePath) throws IOException {
        Map<String, String> params = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            while (reader.ready()) {
                String[] pair = reader.readLine().split(managerGrammar.delimiter());
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        }

        return params;
    }

    private void checkParams() throws CheckParamsException {
        for (int i = 0; i < managerGrammar.numberTokens(); i++) {
            String tokenName = managerGrammar.token(i);
            if (!params.containsKey(tokenName)) {
                throw new CheckParamsException("params doesnt contain token with name " + tokenName);
            }
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
