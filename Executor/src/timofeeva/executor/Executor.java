package timofeeva.executor;

import ru.spbstu.pipeline.BaseGrammar;
import ru.spbstu.pipeline.IExecutable;
import ru.spbstu.pipeline.IExecutor;
import ru.spbstu.pipeline.RC;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
    private Logger logger;
    private IExecutable producer;
    private IExecutable consumer;
    private int bufferSize;
    private byte[] outputBuffer;
    private int bytesInBuffer;

    private void logWarn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    public Executor(Logger logger) {
        this.logger = logger;
    }


    @Override
    public RC execute(byte[] bytes) {
        byte[] newBytes = null;
        if (bytes != null) {
            newBytes = filterBytes(bytes);
        }

        if (newBytes == null) {
            invertBuffer(0, bytesInBuffer);
            RC rc = consumer.execute(outputBuffer);
            if (rc != RC.CODE_SUCCESS) {
                return rc;
            }
            // зануляем буффер
            outputBuffer = null;
            bytesInBuffer = 0;
            // сигнализируем о завершении consumer'у
            return consumer.execute(null);
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
            invertBuffer(0, bytesInBuffer);
            RC rc = consumer.execute(outputBuffer);
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
    public RC setConsumer(IExecutable iExecutable) {
        if (iExecutable == null) {
            logWarn("Consumer is null");
            return RC.CODE_INVALID_ARGUMENT;
        }
        this.consumer = iExecutable;

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IExecutable iExecutable) {
        if (iExecutable == null) {
            logWarn("Producer is null");
            return RC.CODE_INVALID_ARGUMENT;
        }
        this.producer = iExecutable;

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConfig(String s) {
        return getParams(s);
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
