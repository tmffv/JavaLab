package timofeeva.reader;

import ru.spbstu.pipeline.BaseGrammar;
import ru.spbstu.pipeline.IExecutable;
import ru.spbstu.pipeline.IReader;
import ru.spbstu.pipeline.RC;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Reader implements IReader {
    private static final String BUFF_SIZE_PARAM = "BUFFER_SIZE";
    private final Map<String, String> params = new HashMap<>();
    private final BaseGrammar readerGrammar = new BaseGrammar(new String[]{BUFF_SIZE_PARAM}) {
        @Override
        public String delimiter() {
            return super.delimiter();
        }
    };
    private IExecutable consumer;
    private Logger logger;
    private FileInputStream inputStream;
    private Integer bufferSize;
    private byte[] outputBuffer;
    private boolean finishing = false;

    public Reader(Logger logger) {
        this.logger = logger;
    }

    @Override
    public RC setInputStream(FileInputStream fileInputStream) {
        inputStream = fileInputStream;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC execute(byte[] bytes) {
        if (inputStream == null) {
            logWarn("InputStream is null");
            return RC.CODE_INVALID_INPUT_STREAM;
        }

        outputBuffer = new byte[bufferSize];
        int readBytesCount;

        while (true) {
            readBytesCount = readBytes(outputBuffer, bufferSize);
            if (readBytesCount <= 0) {
                // считывание завершилось
                break;
            }

            RC rc = consumer.execute(outputBuffer);
            if (rc != RC.CODE_SUCCESS) {
                return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
            }

            Arrays.fill(outputBuffer, (byte) 0);
        }

        // передаем сигнал о завершении
        consumer.execute(null);

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IExecutable iConsumer) {
        this.consumer = iConsumer;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IExecutable iProducer) {
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConfig(String s) {
        RC rc = getParams(s);
        if (rc != RC.CODE_SUCCESS) {
            return rc;
        }
        rc = checkParams();

        return rc;
    }

    private RC getParams(String filePath) {
        params.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            while (reader.ready()) {
                String[] pair = reader.readLine().split(readerGrammar.delimiter());
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        } catch (IOException e) {
            logWarn("Error while parsing " + this.getClass().getName() + " params");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        return RC.CODE_SUCCESS;
    }

    private RC checkParams() {
        if (params.containsKey(BUFF_SIZE_PARAM)) {
            String buffSizeParam = params.get(BUFF_SIZE_PARAM);
            try {
                bufferSize = Integer.parseInt(buffSizeParam);
                return RC.CODE_SUCCESS;
            } catch (NumberFormatException e) {
                logWarn("Wrong Reader argument value type, expected int");
            }
        }

        return RC.CODE_CONFIG_GRAMMAR_ERROR;
    }

    private void logWarn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    private int readBytes(byte[] buffer, int size) {
        int bytesReadCount;
        try {
            bytesReadCount = inputStream.read(buffer, 0, size);
        } catch (Exception e) {
            logWarn("Error while file reading");
            return -1;
        }

        return bytesReadCount;
    }
}
