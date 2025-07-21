package com.example.botFSSP.services;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.example.botFSSP.config.BotConfig;
import com.example.botFSSP.models.DebtorData;
import com.example.botFSSP.models.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.net.URL;
import java.util.List;
import java.nio.file.StandardCopyOption;


@Component
public class TelegramBot extends TelegramLongPollingBot {
    private static final String RESOURCES_DIR = "src/main/resources/uploads/";
    private static final String TEST_DIR = "src/main/resources/";
    final BotConfig botConfig;
    private InlineKeyboardMarkup inlineKeyboard;
    private int page= 0;
    private List<User> users = new ArrayList<>();
    public TelegramBot(BotConfig botConfig){
        this.botConfig = botConfig;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Начало работы бота"));
        listOfCommands.add(new BotCommand("/expired", "Получение истекших сроков"));
        listOfCommands.add(new BotCommand("/getinfo", "Получение истекшего срока по номеру"));
        listOfCommands.add(new BotCommand("/subscribe", "Подписаться на рассылку"));
        listOfCommands.add(new BotCommand("/unsubscribe", "отписаться от рассылки"));

        try {
            execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        }
        catch (TelegramApiException e){
            System.out.println(e.getMessage());
        }

        // Создаем Inline-клавиатуру
        inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первая строка кнопок
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton button1 = new InlineKeyboardButton();
        button1.setText("Предыдущие 10 долгов");
        button1.setCallbackData("button1_clicked");
        row1.add(button1);

        InlineKeyboardButton button2 = new InlineKeyboardButton();
        button2.setText("Следующие 10 долгов");
        button2.setCallbackData("button2_clicked");
        row1.add(button2);

        rows.add(row1);
        inlineKeyboard.setKeyboard(rows);
    }
    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }
    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
            String callbackData = update.getCallbackQuery().getData();
            long chatID = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals("button1_clicked")) {
                page = page == 0? 0: page--;
                try {
                    getExpired(chatID, page);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            else if (callbackData.equals("button2_clicked")) {
                page++;
                try {
                    getExpired(chatID, page);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        else if (update.hasMessage() && update.getMessage().hasText()){

            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();

            switch (messageText) {
                case "/start":
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "/expired":
                    page = 0;
                    sendMessage(chatId, "Получаю первые 10 истекающих/истекших долга");
                    try {
                        getExpired(chatId, page);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "/getinfo":
                    sendMessage(chatId, "Введите номер ИП");
                    break;
                case "/subscribe":
                    subscribe(chatId, userId);
                    break;
                case "/unsubscribe":
                    unsubscribe(chatId, userId);
                    break;
                default:
                    String text = update.getMessage().getText();
                    DebtorData res = null;
                    try {
                        res = containsInData(text);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (res != null){
                        sendMessage(chatId, res.toString());
                    }
                    else sendMessage(chatId, "Что-то пошло не так");
                    break;
            }
        }

        else if (update.hasMessage() && update.getMessage().hasDocument()) {
            Message message = update.getMessage();
            Document document = message.getDocument();

            try {
                // Получаем путь к файлу в Telegram
                GetFile getFile = new GetFile(document.getFileId());
                String filePath = execute(getFile).getFilePath();

                // Скачиваем файл
                String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;
                if (!document.getFileName().contains(".xlsx")){
                    sendMessage(update.getMessage().getChatId(), "Неправильный формат, нужен .xlsx файл");
                    return;
                }

                String fileName = "Data.xlsx";
                Path destinationPath = Paths.get(TEST_DIR + fileName);

                // Создаем директорию, если её нет
                Files.createDirectories(Paths.get(TEST_DIR));

                // Загружаем файл в папку resources
                try (InputStream in = new URL(fileUrl).openStream()) {
                    Files.copy(in, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Файл сохранен: " + destinationPath);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                checkLoadedData(message.getChatId());
                String fileName = "Data.xlsx";
                Path destinationPath = Paths.get(RESOURCES_DIR + fileName);
                Files.copy(Files.newInputStream(Path.of(TEST_DIR + fileName)), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(Path.of(TEST_DIR + fileName));
            } catch (Exception e) {
                try {
                    Files.delete(Path.of(TEST_DIR + "Data.xlsx"));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                sendMessage(message.getChatId(),
                        "столбцы должны быто названны в определенном порядке: \n" +
                        "№ п/п, " + "Статус, " + "Подразделение ОСП," +"Дата завершения ИП, " +
                        "Регистрационный номер ИП, " +"Дата возбуждения, " + "Взыскатель, " +
                        "Сумма долга, " + "Остаток долга, " + "Тип должника");
                throw new RuntimeException(e);
            }
        }
    }
    private void subscribe(long chatId, long userId){
        User user = new User(userId, chatId);
        for (User u : users){
            if (u.getId() == userId) {
                sendMessage(chatId, "Вы уже подписанны");
                return;
            }
        }
        users.add(user);
        sendMessage(chatId, "Вы подписанны");
    }
    private void unsubscribe (long chatId, long userId){
        for (User u : users){
            if (u.getId() == userId) {
                users.remove(u);
                sendMessage(chatId, "Вы отписанны");
                return;
            }
        }
        sendMessage(chatId, "Вы не подписанны");
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void sendDailyMessage() throws IOException {
        String message = "Ежедневная рассылка: \n" +  getTodayExpired();

        for (User user : users) {
            sendMessage(user.getChatId(), message);
        }
    }

    private void startCommandReceived(long chatId, String name){
        String answer = "Hi " + name + ", nice to meet you!";

        sendMessage(chatId, answer);
    }
    private void sendExpired(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        message.setReplyMarkup(inlineKeyboard);

        try{
            execute(message);
        }
        catch (TelegramApiException e){
            System.out.println(e.getMessage());
        }
    }
    private void sendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);

        try{
            execute(message);
        }
        catch (TelegramApiException e){
            System.out.println(e.getMessage());
        }
    }
    private String getTodayExpired() throws IOException {
        Path filePath = Paths.get(RESOURCES_DIR + "Data.xlsx");
        InputStream data = Files.newInputStream(filePath);
        List<DebtorData> result = new ArrayList<>();
        final String[] text = new String[1];

        EasyExcel.read(data, DebtorData.class, new ReadListener<DebtorData>() {
            @Override
            public void invoke(DebtorData data, AnalysisContext context) {
                Period period = Period.between(data.getStartDate().toLocalDate(), LocalDateTime.now().toLocalDate());

                if (period.getYears() == 2 && period.getMonths() == 11 && period.getDays() == 0) {
                    result.add(data);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                if (result.isEmpty()) {
                    text[0] = "Сегодня нет истекающих долгов!";
                    return;
                }
                String res = "";
                // Здесь обрабатываем накопленные данные
                for (Object obj : result) {
                    res += obj.toString() + "\n\n";
                    System.out.println(obj);
                }
                text[0] = res;
            }
        }).excelType(ExcelTypeEnum.XLSX).sheet().doRead();
        return text[0];
    }
    private DebtorData containsInData(String number) throws IOException {
        Path filePath = Paths.get(RESOURCES_DIR + "Data.xlsx");
        InputStream data = Files.newInputStream(filePath);

        List<DebtorData> result = new ArrayList<>();
        EasyExcel.read(data, DebtorData.class, new ReadListener<DebtorData>() {

            @Override
            public void invoke(DebtorData data, AnalysisContext context) {
                if (!result.isEmpty()) return;

                if (data.getNumber().equals(number)) {
                    result.add(data);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                if (result.isEmpty()) result.add(null);

                System.out.println(result.get(0));
            }
        }).excelType(ExcelTypeEnum.XLSX).sheet().doRead();

        return result.get(0);
    }
    private void getExpired(long chatID, int page) throws IOException {
        Path filePath = Paths.get(RESOURCES_DIR + "Data.xlsx");
        InputStream data = Files.newInputStream(filePath);
        final int maxRows = 10;
        List<DebtorData> result = new ArrayList<>();

        EasyExcel.read(data, DebtorData.class, new ReadListener<DebtorData>() {
            private int rowCount = 0;
            private int counter = 0;
            @Override
            public void invoke(DebtorData data, AnalysisContext context) {
                try {
                    if (rowCount < maxRows) {
                        Period period = Period.between(data.getStartDate().toLocalDate(), LocalDateTime.now().toLocalDate());

                        if (period.getYears() == 2 && period.getMonths() >= 11 || period.getYears() >= 3) {
                            if (counter < page * 10) {
                                counter++;
                            } else {
                                result.add(data);
                                rowCount++;
                            }
                        }
                    }
                }
                catch (Exception e){
                    sendMessage(chatID, "столбцы должны быто названны в определенном порядке: \n" +
                            "№ п/п, " + "Статус, " + "Подразделение ОСП," +"Дата завершения ИП, " +
                            "Регистрационный номер ИП, " +"Дата возбуждения, " + "Взыскатель, " +
                            "Сумма долга, " + "Остаток долга, " + "Тип должника");
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                String res = "";
                // Здесь обрабатываем накопленные данные
                for (Object obj : result) {
                    res += obj.toString() + "\n\n";
                    System.out.println(obj);
                }
                sendExpired(chatID, res);

                System.out.println("Прочитано " + rowCount + " строк");
            }
        }).excelType(ExcelTypeEnum.XLSX).sheet().doRead();

    }

    private void checkLoadedData(long chatID) throws IOException {
        Path filePath = Paths.get(TEST_DIR + "Data.xlsx");
        InputStream data = Files.newInputStream(filePath);
        EasyExcel.read(data, DebtorData.class, new ReadListener<DebtorData>() {
            boolean flag = true;
            @Override
            public void invoke(DebtorData data, AnalysisContext context) {
                try {
                    if (flag){
                        System.out.println(data.getNumber());
                        flag = !flag;
                    }
                }
                catch (Exception e){
                    sendMessage(chatID, "столбцы должны быто названны в определенном порядке: \n" +
                            "№ п/п, " + "Статус, " + "Подразделение ОСП," +"Дата завершения ИП, " +
                            "Регистрационный номер ИП, " +"Дата возбуждения, " + "Взыскатель, " +
                            "Сумма долга, " + "Остаток долга, " + "Тип должника");
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {

            }
        }).excelType(ExcelTypeEnum.XLSX).sheet().doRead();
    }
}
