import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Тестовое задание:
 * 1) Один поток пишет рандомное чётное число в общий файл.
 * 2) Второй поток пишет рандомное нечётное число в тот же файл.
 * 3) Третий поток постоянно читает файл и выводит в консоль последние появившиеся числа.
 *
 * Все классы, тесты, логирование - в одном файле для наглядности.
 */
public class Main {

    // Имя файла для записи и чтения
    private static final String FILE_NAME = "numbers.txt";

    // Логгер для вывода информации о работе
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    // Используем общий замок (lock), чтобы синхронизировать доступ к файлу
    private static final ReentrantLock fileLock = new ReentrantLock();

    public static void main(String[] args) {
        // Настраиваем простейший вывод логов в консоль
        configureLogger();

        // Создаём (или очищаем) файл перед началом работы
        clearFile(FILE_NAME);

        // Создаём потоки
        Thread evenWriter = new Thread(new EvenWriter(FILE_NAME), "EvenWriter");
        Thread oddWriter = new Thread(new OddWriter(FILE_NAME),   "OddWriter");
        Thread reader    = new Thread(new Reader(FILE_NAME),      "Reader");

        // Запускаем потоки
        evenWriter.start();
        oddWriter.start();
        reader.start();

        // Даём потокам поработать 10 секунд
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Главный поток прерван во время ожидания", e);
        }

        // После 10 секунд прерываем работу
        evenWriter.interrupt();
        oddWriter.interrupt();
        reader.interrupt();

        // Дожидаемся завершения потоков
        try {
            evenWriter.join();
            oddWriter.join();
            reader.join();
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Главный поток прерван во время join", e);
        }

        // Запускаем примитивный тест, чтобы убедиться, что файл не пустой
        testFileNotEmpty(FILE_NAME);

        logger.info("Завершение работы программы.");
    }

    /**
     * Поток записи случайных чётных чисел.
     */
    static class EvenWriter implements Runnable {
        private final String fileName;
        private final Random random = new Random();

        public EvenWriter(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                // Генерируем случайное чётное число (0..98)
                int evenNumber = random.nextInt(50) * 2;

                fileLock.lock();
                try (FileWriter writer = new FileWriter(fileName, true)) {
                    writer.write(evenNumber + " ");
                    logger.info(Thread.currentThread().getName() + " записал чётное число: " + evenNumber);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Ошибка при записи чётного числа в файл", e);
                } finally {
                    fileLock.unlock();
                }
                // Небольшая пауза
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Если прерывание - выходим из цикла
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Поток записи случайных нечётных чисел.
     */
    static class OddWriter implements Runnable {
        private final String fileName;
        private final Random random = new Random();

        public OddWriter(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                // Генерируем случайное нечётное число (1..99)
                int oddNumber = random.nextInt(50) * 2 + 1;

                fileLock.lock();
                try (FileWriter writer = new FileWriter(fileName, true)) {
                    writer.write(oddNumber + " ");
                    logger.info(Thread.currentThread().getName() + " записал нечётное число: " + oddNumber);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Ошибка при записи нечётного числа в файл", e);
                } finally {
                    fileLock.unlock();
                }

                // Небольшая пауза
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Поток, который читает файл и выводит последние появившиеся числа.
     * Для простоты будем читать весь файл, брать последние несколько токенов
     * и выводить их в лог.
     */
    static class Reader implements Runnable {
        private final String fileName;

        public Reader(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                fileLock.lock();
                try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append(" ");
                    }
                    // Разбиваем всё содержимое на "токены"
                    String content = sb.toString().trim();
                    if (!content.isEmpty()) {
                        String[] tokens = content.split("\\s+");
                        // Берём последние несколько чисел или хотя бы последнее
                        int countToShow = Math.min(5, tokens.length);
                        StringBuilder lastNumbers = new StringBuilder();
                        for (int i = tokens.length - countToShow; i < tokens.length; i++) {
                            lastNumbers.append(tokens[i]).append(" ");
                        }
                        logger.info(Thread.currentThread().getName()
                                + " прочитал последние числа: " + lastNumbers.toString().trim());
                    } else {
                        logger.info(Thread.currentThread().getName()
                                + " файл пуст, пока нечего читать.");
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Ошибка при чтении файла", e);
                } finally {
                    fileLock.unlock();
                }

                // Небольшая пауза
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Настройка логгера для вывода в консоль.
     */
    private static void configureLogger() {
        // Упрощённая настройка логгера, чтобы все сообщения шли в консоль
        String config =
                "handlers=java.util.logging.ConsoleHandler\n" +
                        ".level=ALL\n" +
                        "java.util.logging.ConsoleHandler.level=ALL\n";
        try {
            LogManager.getLogManager().readConfiguration(
                    new ByteArrayInputStream(config.getBytes())
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Удаляем или очищаем файл, чтобы начать с "чистого листа".
     */
    private static void clearFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            if (!file.delete()) {
                logger.warning("Не удалось удалить существующий файл: " + fileName);
            }
        }
        // Файл будет создан заново при первой записи
    }

    /**
     * Простейший тест, который проверяет, что файл не пуст.
     * Если пуст - бросаем RuntimeException.
     */
    private static void testFileNotEmpty(String fileName) {
        File file = new File(fileName);
        if (!file.exists() || file.length() == 0) {
            throw new RuntimeException("Тест не пройден: файл пустой или не создан!");
        } else {
            logger.info("Тест пройден: файл не пустой, значит запись прошла успешно.");
        }
    }
}
