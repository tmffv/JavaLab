package timofeeva.manager;

import ru.spbstu.pipeline.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

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
            return Arrays.stream(Parameters.values()).map((Enum::name)).toArray(String[]::new);
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
    private IReader reader;
    private final List<IExecutor> executors = new LinkedList<>();
    private IWriter writer;
    FileInputStream fileInputStream = null;
    FileOutputStream fileOutputStream = null;

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
        RC resultCode = prepareComponents();
        if (resultCode != RC.CODE_SUCCESS) {
            return resultCode;
        }

        resultCode = prepareStreams();
        if (resultCode != RC.CODE_SUCCESS) {
            return resultCode;
        }

        try {
            resultCode = reader.execute();
        } catch (Exception e) {
            return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        }

        return resultCode;
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

    private RC prepareComponents() {
        return prepareReader();
    }

    private RC prepareStreams() {
        try {
            fileInputStream = new FileInputStream(params.get(Parameters.INPUT_FILE_PATH.name()));
        } catch (Exception e) {
            logWarning("file with name " + params.get(Parameters.INPUT_FILE_PATH.name()) + "doesnt exist");
            return RC.CODE_INVALID_INPUT_STREAM;
        }

        try {
            fileOutputStream = new FileOutputStream(params.get(Parameters.OUTPUT_FILE_PATH.name()));
        } catch (Exception e) {
            logWarning("file with name " + params.get(Parameters.OUTPUT_FILE_PATH.name()) + "doesnt exist");
            return RC.CODE_INVALID_OUTPUT_STREAM;
        }

        RC rc = reader.setInputStream(fileInputStream);
        if (rc != RC.CODE_SUCCESS) {
            return rc;
        }
        rc = writer.setOutputStream(fileOutputStream);

        return rc;
    }

    private RC prepareReader() {
        try {
            reader = (IReader) getObjectWithClassName(params.get(Parameters.READER_NAME.name()));
            RC rc = reader.setConfig(params.get(Parameters.READER_CONFIG_PATH.name()));
            if (rc != RC.CODE_SUCCESS) {
                return rc;
            }
        } catch (Exception e) {
            logWarning("Error while timofeeva.reader initialization");
            return RC.CODE_CONFIG_SEMANTIC_ERROR;
        }
        return prepareExecutors();
    }

    private RC prepareExecutors() {
        String[] executorsConfigPaths = params.get(Parameters.EXECUTOR_CONFIG_PATH.name()).split(",");
        String[] executorClassNames = params.get(Parameters.EXECUTOR_NAME.name()).split(",");
        for (int i = 0; i < executorClassNames.length; i++) {
            try {
                IExecutor executor = (IExecutor) getObjectWithClassName(executorClassNames[i]);
                RC rc = executor.setConfig(executorsConfigPaths[i]);
                if (rc != RC.CODE_SUCCESS) {
                    return rc;
                }
                executors.add(executor);
            } catch (Exception e) {
                logWarning("Error while executors initialization");
                return RC.CODE_CONFIG_SEMANTIC_ERROR;
            }
        }

        return prepareWriter();
    }

    private RC prepareWriter() {
        try {
            writer = (IWriter) getObjectWithClassName(params.get(Parameters.WRITER_NAME.name()));
            writer.setConfig(params.get(Parameters.WRITER_CONFIG_PATH.name()));
        } catch (Exception e) {
            logWarning("Error while timofeeva.writer initialization");
            return RC.CODE_CONFIG_SEMANTIC_ERROR;
        }

        return linkComponents();
    }

    private RC linkComponents() {
        return linkReaderExecutor();
    }

    private RC linkReaderExecutor() {
        RC rc = reader.setConsumer(executors.get(0));
        if (rc != RC.CODE_SUCCESS) {
            return rc;
        }
        rc = executors.get(0).setProducer(reader);
        if (rc != RC.CODE_SUCCESS) {
            return rc;
        }

        return linkExecutorsWithWriter();
    }

    private RC linkExecutorsWithWriter() {
        RC rc;
        for (int i = 0; i < executors.size() - 1; i++) {
            IExecutor firstExecutor = executors.get(i);
            IExecutor secondExecutor = executors.get(i + 1);
            rc = firstExecutor.setConsumer(secondExecutor);
            if (rc != RC.CODE_SUCCESS) {
                return rc;
            }
            rc = secondExecutor.setProducer(firstExecutor);
            if (rc != RC.CODE_SUCCESS) {
                return rc;
            }
        }
        IExecutor lastExecutor = executors.get(executors.size() - 1);
        rc = writer.setProducer(lastExecutor);
        if (rc != RC.CODE_SUCCESS) {
            return rc;
        }
        rc = lastExecutor.setConsumer(writer);
        if (rc != RC.CODE_SUCCESS) {
            return rc;
        }

        return RC.CODE_SUCCESS;
    }

    private Object getObjectWithClassName(String className) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class clazz = Class.forName(className);
        return clazz.getConstructor(Logger.class).newInstance(logger);
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
