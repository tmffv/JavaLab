package timofeeva.reader;

import ru.spbstu.pipeline.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
    private final IMediator mediatorByte = new IMediator() {
        @Override
        public Object getData() {
            if (outputBuffer != null) {
                return outputBuffer.clone();
            }
            return null;
        }
    };
    private final IMediator mediatorShort = new IMediator() {
        @Override
        public Object getData() {
            if (outputBuffer != null) {
                short[] shorts = new short[outputBuffer.length / 2];
                ByteBuffer.wrap(outputBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                return shorts;
            }
            return null;
        }
    };
    private final IMediator mediatorChar = new IMediator() {
        @Override
        public Object getData() {
            if (outputBuffer != null) {
                String text = new String(outputBuffer, StandardCharsets.UTF_8);
                return text.toCharArray();
            }
            return null;
        }
    };
    private IConsumer consumer;
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
    public RC execute() {
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

            RC rc = consumer.execute();
            if (rc != RC.CODE_SUCCESS) {
                return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
            }

            Arrays.fill(outputBuffer, (byte) 0);
        }

        // передаем сигнал о завершении
        outputBuffer = null;
        consumer.execute();

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IConsumer iConsumer) {
        this.consumer = iConsumer;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IProducer iProducer) {
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

    @Override
    public TYPE[] getOutputTypes() {
        return new TYPE[] {TYPE.BYTE, TYPE.SHORT};
    }

    @Override
    public IMediator getMediator(TYPE type) {
        return switch (type) {
            case BYTE -> mediatorByte;
            case CHAR -> mediatorChar;
            case SHORT -> mediatorShort;
        };
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
