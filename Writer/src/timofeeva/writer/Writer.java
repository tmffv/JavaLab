package timofeeva.writer;

import ru.spbstu.pipeline.*;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Writer implements IWriter {
    private static final String BUFF_SIZE_PARAM = "BUFFER_SIZE";
    private static final TYPE[] supportedTypes = new TYPE[]{TYPE.BYTE, TYPE.SHORT, TYPE.CHAR};
    private final Map<String, String> params = new HashMap<>();
    private final BaseGrammar writerGrammar = new BaseGrammar(new String[]{BUFF_SIZE_PARAM}) {
        @Override
        public String delimiter() {
            return super.delimiter();
        }
    };
    private IProducer producer;
    private Logger logger;
    private FileOutputStream outputStream;
    private Integer bufferSize;
    private byte[] buffer;
    private int bytesInBuffer;// кол-во занятых байт в буффере
    private IMediator producerMediator;
    private TYPE producerMediatorType;

    public Writer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public RC setOutputStream(FileOutputStream fileOutputStream) {
        if (fileOutputStream == null) {
            logWarn("fileOutputStream is null " + Writer.class.getName());
            return RC.CODE_INVALID_ARGUMENT;
        }
        outputStream = fileOutputStream;

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC execute() {
        if (producerMediator == null) {
            logWarn("Writer doesnt support Producer's data types");
            return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
        }

        Object data = producerMediator.getData();
        if (data == null) {
            writeDataFromBufferAndClear();
            return RC.CODE_SUCCESS;
        }

        byte[] bytesInput = convertInputDataTyBytes(data);
        if (bytesInput == null) {
            return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
        }

        long delta = bufferSize - bytesInBuffer;
        // если места в буффере хваает
        if (delta >= bytesInput.length) {
            fillBuffer(bytesInBuffer, bytesInput);
            return RC.CODE_SUCCESS;
        }

        // если места в буффере не хватает, выводим то что есть в буффере + то что в него вошло бы
        byte[] bufferPlusNewData = Arrays.copyOf(buffer, bytesInBuffer + bytesInput.length);
        System.arraycopy(bytesInput, 0, bufferPlusNewData, bytesInBuffer, bytesInput.length);
        int offset = bufferPlusNewData.length % bufferSize; // кол-во последних байт, которые нужно записать в буффер
//        if (offset == 0) {
//            offset = bufferSize;
//        }

//        byte[] bytesToWrite = Arrays.copyOf(bufferPlusNewData, bufferPlusNewData.length - offset);
        writeData(bufferPlusNewData, bufferPlusNewData.length - offset);

        buffer = new byte[bufferSize];
        bytesInBuffer = offset;
        System.arraycopy(bufferPlusNewData, bufferPlusNewData.length - offset, buffer, 0, offset);

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IConsumer iConsumer) {
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IProducer iProducer) {
        if (iProducer == null) {
            logWarn("producer is null " + Writer.class.getName());
            return RC.CODE_INVALID_ARGUMENT;
        }
        producer = iProducer;
        for (TYPE inputType : supportedTypes) {
            for (TYPE outputType : producer.getOutputTypes()) {
                if (inputType == outputType) {
                    producerMediatorType = outputType;
                    producerMediator = producer.getMediator(outputType);
                    return RC.CODE_SUCCESS;
                }
            }
        }

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConfig(String s) {
        return parseArgs(s);
    }

    private RC parseArgs(String filePath) {
        params.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            while (reader.ready()) {
                String[] pair = reader.readLine().split(writerGrammar.delimiter());
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        } catch (IOException e) {
            logWarn("Error while parsing " + this.getClass().getName() + " params");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        if (params.containsKey(BUFF_SIZE_PARAM)) {
            String s = params.get(BUFF_SIZE_PARAM);
            try {
                bufferSize = Integer.parseInt(s);
                buffer = new byte[bufferSize];
                return RC.CODE_SUCCESS;
            } catch (NumberFormatException e) {
                logWarn("Error while parsing " + this.getClass().getName() + " ,param " + BUFF_SIZE_PARAM + "must be int type");
            }
        }

        return RC.CODE_CONFIG_GRAMMAR_ERROR;
    }

    private void writeDataFromBufferAndClear() {
        if (buffer != null && bytesInBuffer > 0) {
            writeData(buffer, bytesInBuffer);
            bytesInBuffer = 0;
            buffer = new byte[bufferSize];
        }
    }

    /**
     * @param data - данные для вывода в outputStream
     * @param len  - кол-во байт для вывода
     */
    private void writeData(byte[] data, int len) {
        try {
            for (int i = 0; i < len; i++) {
                byte b = data[i];
                if (b != (byte) 0) {
                    outputStream.write(b);
                }
            }
        } catch (IOException e) {
            logWarn("Error while writing data to file");
        }
    }

    private void fillBuffer(int offset, byte[] data) {
        System.arraycopy(data, 0, buffer, offset, data.length);
        bytesInBuffer = offset + data.length;
    }

    private byte[] convertInputDataTyBytes(Object data) {
        try {
            switch (producerMediatorType) {
                case BYTE:
                    return (byte[]) data;
                case SHORT:
                    short[] shortData = (short[]) data;
                    byte[] byteData = new byte[shortData.length * 2];
                    for (int i = 0, j = 0; i < byteData.length; i += 2, j++) {
                        short shortValue = shortData[j];
                        byteData[i] = (byte) (shortValue & 0xff);
                        byteData[i + 1] = (byte) ((shortValue >> 8) & 0xff);
                    }
                    return byteData;
                case CHAR:
                    return new String((char[]) data).getBytes(StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            logWarn("Error while converting data in Writer");
        }

        return null;
    }

    private void logWarn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
