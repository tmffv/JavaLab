package timofeeva.executor;

import ru.spbstu.pipeline.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Executor implements IExecutor {
    private static final String BUFFER_SIZE_PARAM = "BUFFER_SIZE";
    private final Map<String, String> params = new HashMap<>();
    private final BaseGrammar executorGrammar = new BaseGrammar(new String[]{BUFFER_SIZE_PARAM}) {
        @Override
        public String delimiter() {
            return super.delimiter();
        }
    };
    private final IMediator mediatorByte = () -> {
        if (this.outputBuffer != null) {
            invertBuffer(0, this.bytesInBuffer);
            return this.outputBuffer.clone();
        }
        return null;
    };
    private final IMediator mediatorShort = () -> {
        if (Executor.this.outputBuffer != null) {
            invertBuffer(0, this.bytesInBuffer);
            short[] shorts = new short[this.outputBuffer.length / 2];
            ByteBuffer.wrap(this.outputBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            return shorts;
        }
        return null;
    };
    private final IMediator mediatorChar = () -> {
        if (Executor.this.outputBuffer != null) {
            String text = new String(Executor.this.outputBuffer, StandardCharsets.UTF_8);
            return text.toCharArray();
        }
        return null;
    };
    private Logger logger;
    private IProducer producer;
    private IConsumer consumer;
    private IMediator producerMediator;
    private int bufferSize;
    private byte[] outputBuffer;
    private int bytesInBuffer;
    private TYPE producerType;

    private void logWarn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    public Executor(Logger logger) {
        this.logger = logger;
    }

    @Override
    public RC execute() {
        Object data = producerMediator.getData();
        byte[] newBytes = convertInputDataTyBytes(data);

        if (newBytes == null) {
            RC rc = consumer.execute();
            if (rc != RC.CODE_SUCCESS) {
                return rc;
            }
            // зануляем буффер
            outputBuffer = null;
            bytesInBuffer = 0;
            // сигнализируем о завершении consumer'у
            return consumer.execute();
        }

        // если в буффер все не поместится
        byte[] bufferPlusNewData = Arrays.copyOf(outputBuffer, bytesInBuffer + newBytes.length);
        System.arraycopy(newBytes, 0, bufferPlusNewData, bytesInBuffer, newBytes.length);
        if (bufferSize - bytesInBuffer < newBytes.length) {
            // заполняем буффер
//            System.arraycopy(newBytes, bytesInBuffer, bufferPlusNewData, 0, newBytes.length);
            int offset = bufferPlusNewData.length % bufferSize; // кол-во байт, которые останутся в буффере
//            if (offset == 0) {
//                offset = bufferSize;
//            }

            //данные которе не помещаются в буффер
            byte[] exportBytes = Arrays.copyOf(bufferPlusNewData, bufferPlusNewData.length - offset);

            // пытаемся передать все байты дальше
            outputBuffer = exportBytes;
            RC rc = consumer.execute();
            if (rc != RC.CODE_SUCCESS) {
                return rc;
            }
            // если получилось передать - записываем в буффер ОСТАВШИЕСЯ байты
            outputBuffer = new byte[bufferSize];
            System.arraycopy(newBytes, newBytes.length - offset, outputBuffer, 0, offset);
            bytesInBuffer = offset;
            invertBuffer(0, offset);
        } else {
            // помещаем все в буффер
            outputBuffer = new byte[bufferSize];
            System.arraycopy(bufferPlusNewData, 0, outputBuffer, 0, bufferPlusNewData.length);
            invertBuffer(bytesInBuffer, bufferPlusNewData.length);
            bytesInBuffer = bufferPlusNewData.length;
        }
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IConsumer iConsumer) {
        if (iConsumer == null) {
            logWarn("Consumer is null");
            return RC.CODE_INVALID_ARGUMENT;
        }
        this.consumer = iConsumer;

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IProducer iProducer) {
        if (iProducer == null) {
            logWarn("Producer is null");
            return RC.CODE_INVALID_ARGUMENT;
        }
        for (TYPE type : new TYPE[]{TYPE.BYTE, TYPE.SHORT, TYPE.CHAR}) {
            for (TYPE supportedProducerType : iProducer.getOutputTypes()) {
                if (type == supportedProducerType) {
                    producerType = supportedProducerType;
                    producerMediator = iProducer.getMediator(supportedProducerType);
                    producer = iProducer;
                    return RC.CODE_SUCCESS;
                }
            }
        }

        logWarn("Executor doesnt support producer types");
        return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
    }

    @Override
    public RC setConfig(String s) {
        return getParams(s);
    }

    @Override
    public TYPE[] getOutputTypes() {
        return new TYPE[]{TYPE.BYTE, TYPE.SHORT, TYPE.CHAR};
    }

    @Override
    public IMediator getMediator(TYPE type) {
        return switch (type) {
            case BYTE -> mediatorByte;
            case CHAR -> mediatorChar;
            case SHORT -> mediatorShort;
        };
    }

    private byte[] convertInputDataTyBytes(Object data) {
        try {
            switch (producerType) {
                case BYTE:
                    return filterBytes((byte[]) data);
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

    private RC getParams(String filePath) {
        this.params.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            while (reader.ready()) {
                String[] pair = reader.readLine().split(executorGrammar.delimiter());
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        } catch (IOException e) {
            logWarn("Error while parsing " + this.getClass().getName() + " params");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        if (params.containsKey(BUFFER_SIZE_PARAM)) {
            try {
                bufferSize = Integer.parseInt(params.get(BUFFER_SIZE_PARAM));
                outputBuffer = new byte[bufferSize];
                return RC.CODE_SUCCESS;
            } catch (NumberFormatException e) {
                logWarn("Executor param must be int type");
                return RC.CODE_CONFIG_GRAMMAR_ERROR;
            }
        }

        return RC.CODE_SUCCESS;
    }

    private void invertBuffer(int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
//            System.out.println("b:" + Integer.toBinaryString((outputBuffer[i] & 0xFF) + 0x100).substring(1));
            outputBuffer[i] = (byte) (~outputBuffer[i] & 0xff);
//            System.out.println("a:" + Integer.toBinaryString((outputBuffer[i] & 0xFF) + 0x100).substring(1));
        }
    }

    private byte[] filterBytes(byte[] input) {
        ArrayList<Byte> output = new ArrayList<>();
        for (byte b : input) {
            if (b != 0) {
                output.add(b);
            }
        }
        byte[] newArr = new byte[output.size()];
        for (int i = 0; i < output.size(); i++) {
            newArr[i] = output.get(i);
        }

        return newArr;
    }
}
