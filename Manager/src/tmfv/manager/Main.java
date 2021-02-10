package tmfv.manager;

import ru.spbstu.pipeline.RC;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
    private static Logger logger = Logger.getLogger("Logger");

    public static void main(String[] args) {
        try {
            configureLogger();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        RC exitCode = prepareArgs(args);
        if (exitCode == RC.CODE_SUCCESS) {
            logger.info(exitCode.name());
        } else {
            logger.warning(exitCode.name());
        }
    }

    // конфигурируем логгер
    private static void configureLogger() throws IOException {
        FileHandler fileHandler = new FileHandler("sessionLogs.txt");
        logger.addHandler(fileHandler);
        fileHandler.setFormatter(new SimpleFormatter());
    }

    // проверяем аргументы
    private static RC prepareArgs(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].length() == 0) {
            logger.warning("Wrong args");
            return RC.CODE_INVALID_ARGUMENT;
        }

        return prepareManager(args[0]);
    }

    // инициализируем и запускаем менеджер
    private static RC prepareManager(String configFilePath) {
        Manager manager = new Manager();
        manager.setLogger(logger);
        RC rc = manager.setConfig(configFilePath);
        if (rc != RC.CODE_SUCCESS) {
            return rc;
        }

        return manager.start();
    }
}
