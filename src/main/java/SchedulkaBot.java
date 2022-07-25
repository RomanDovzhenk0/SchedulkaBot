import lombok.SneakyThrows;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;


public class SchedulkaBot extends TelegramLongPollingBot{

    private Message currentMessage;

    private List<Course> courses;

    private String course;

    private Group group;

    private boolean isFileLoaded = false;

    private Thread currentThread;

    @SneakyThrows
    public void onUpdateReceived(Update update) {
        if(update.hasMessage()) {
            currentMessage = update.getMessage();
            if(currentMessage.hasText()) {
                String text = currentMessage.getText();
                if(text.equals("Меню")) {
                    execute(DeleteMessage.builder().chatId(currentMessage.getChatId()).messageId(currentMessage.getMessageId()).build());
                    showMenu();
                }
            } else if(currentMessage.hasEntities()) {
                Optional<MessageEntity> command = currentMessage
                        .getEntities()
                        .stream()
                        .filter(e -> "bot_command".equals(e.getType()))
                        .findFirst();
                command.ifPresent(this::handleCommand);
            } else if (currentMessage.hasDocument() && !isFileLoaded) {
                isFileLoaded = true;
                handleFile();
            }
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    @SneakyThrows
    private void handleCommand(MessageEntity command) {
        switch (command.getText()) {
            case "/start" -> {
                isFileLoaded = false;
                String welcomeMessage = "Привіт, " + currentMessage.getChat().getFirstName() +
                        "\uD83D\uDC4B\nЯ – Schedulka, твоя персональна помічниця!" +
                        "\nРозроблена для зручного доступу до розкладу занять факультету міжнародних відносин\uD83D\uDE09";
                sendTextMessage(welcomeMessage);
                sendTextMessage("Надішли будь ласка .xlsx таблицю твого розкладу \uD83D\uDCE5");
            }
        }
    }

    @SneakyThrows
    private void handleCallback(CallbackQuery callbackQuery) {
        Message message = callbackQuery.getMessage();
        String[] parts = callbackQuery.getData().split(":");
        String action = parts[0];
        String param = parts[1];
        switch (action) {
            case "Course" -> {
                execute(DeleteMessage.builder().chatId(message.getChatId()).messageId(message.getMessageId()).build());
                course = param;
                chooseGroup();
            }
            case "Group" -> {
                execute(DeleteMessage.builder().chatId(message.getChatId()).messageId(message.getMessageId()).build());
                if(param.equals("backToCourse")) {
                    chooseCourse();
                } else {
                    group = courses.stream().filter(e -> e.getName().equals(course)).findFirst().get().getGroups().
                            stream().filter(e -> e.getName().equals(param)).findFirst().get();
                    startThread();
                    addMarkupButton();
                    showMenu();
                }
            }
            case "Menu" -> {
                execute(DeleteMessage.builder().chatId(message.getChatId()).messageId(message.getMessageId()).build());
                switch (param) {
                    case "backToMenu" -> showMenu();
                    case "getScheduleToday" -> showScheduleToday();
                    case "getSchedule" -> showSchedule();
                    case "notifySettings" -> selectNotifySettingsDay();
                    case "handleFile" -> {
                        sendTextMessage("Очікую новий файл \uD83D\uDE34");
                        isFileLoaded = false;
                    }
                    case "chooseGroup" -> chooseCourse();
                }
            }
            case "NotifySettingsDay" -> {
                execute(DeleteMessage.builder().chatId(message.getChatId()).messageId(message.getMessageId()).build());
                showNotifySettings(param, false);
            }
            case "NotifySettings" -> {
                if(parts[1].equals("UnmuteAll")) {
                    group.getLessons().stream()
                            .filter(e -> parts[2].endsWith(e.getDay().substring(2)))
                            .forEach(e -> e.setNotified(true));
                    showNotifySettings(parts[2], true);
                } else if (parts[1].equals("MuteAll")) {
                    group.getLessons().stream()
                            .filter(e -> parts[2].endsWith(e.getDay().substring(2)))
                            .forEach(e -> e.setNotified(false));
                    showNotifySettings(parts[2], true);
                } else {
                    Lesson lesson = group.getLessons().stream().filter(e -> {
                        return parts[1].equals(e.getDay()) && parts[2].equals(String.valueOf(e.getNumber()));
                    }).findFirst().get();
                    lesson.changeNotified();
                    showNotifySettings(lesson.getDay(), true);
                }
            }
        }
    }

    @SneakyThrows
    private void handleFile() {
        Document document = currentMessage.getDocument();
        org.telegram.telegrambots.meta.api.objects.File file
                = execute(GetFile.builder().fileId(document.getFileId()).build());
        if(!document.getFileName().endsWith(".xlsx")) {
            sendTextMessage("Неправильний формат файлу \uD83E\uDD37\u200D♀");
            return;
        }
        Integer messageId = execute(SendMessage.builder().chatId(currentMessage.getChatId())
                .text("Зачекай хвилинку я опрацьовую файл ☺️").build()).getMessageId();

        java.net.URL url = new URL("https://api.telegram.org/file/bot5521154895:AAFZY1fcZy8Diebc6d4-0sP0HjgNk-kfNxQ/" + file.getFilePath());
        OPCPackage opcPackage = OPCPackage.open(url.openStream());

        ScheduleHandler scheduleHandler = new ScheduleHandler(new XSSFWorkbook(opcPackage));
        courses = scheduleHandler.getCourses();
        chooseCourse();
        execute(DeleteMessage.builder().chatId(currentMessage.getChatId()).messageId(messageId).build());
    }

    @SneakyThrows
    private void chooseCourse() {
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (Course course : courses) {
            buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                    .text(course.getName())
                    .callbackData("Course:" + course.getName())
                    .build()));
        }
        execute(SendMessage.builder()
                .chatId(currentMessage.getChatId())
                .text("Ось що мені вдалося знайти \uD83D\uDD0D\nОбери свій курс \uD83E\uDDD1\u200D\uD83C\uDF93")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build())
                .build()
        );
    }

    @SneakyThrows
    private void chooseGroup() {
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        List<Group> courseList = courses.stream()
                                        .filter(e -> e.getName().equals(course))
                                        .findFirst()
                                        .get().getGroups();
        int index = 0;
        for(int i = 0; i < courseList.size(); i++) {
            List<InlineKeyboardButton> buttonList = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                try {
                    buttonList.add(InlineKeyboardButton.builder().text(courseList.get(index).getName())
                            .callbackData("Group:" + courseList.get(index).getName()).build());
                    index++;
                } catch (Exception ignored) {}
            }
            buttons.add(buttonList);
        }
        buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("Group:backToCourse")
                .build()));
        execute(SendMessage.builder()
                .chatId(currentMessage.getChatId())
                .text("Обери свою групу \uD83C\uDF93")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build())
                .build()
        );
    }

    @SneakyThrows
    private void showMenu() {
        String message = "\uD83D\uDE1A " + currentMessage.getChat().getFirstName() + " " + currentMessage.getChat().getLastName()
                + "\n" + course + " " + group.getName();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        buttons.add(Arrays.asList(
                InlineKeyboardButton.builder()
                        .text("\uD83D\uDCC5 Розклад на сьогодні")
                        .callbackData("Menu:getScheduleToday")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("7️⃣ Розклад на тиждень")
                        .callbackData("Menu:getSchedule")
                        .build()));
        buttons.add(Arrays.asList(
                InlineKeyboardButton.builder()
                        .text("\uD83D\uDD14 Налаштування повідомлень")
                        .callbackData("Menu:notifySettings")
                        .build()));
        buttons.add(Arrays.asList(
                InlineKeyboardButton.builder()
                        .text("\uD83D\uDCC4 Змінити файл")
                        .callbackData("Menu:handleFile")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("\uD83D\uDE27 Змінити групу")
                        .callbackData("Menu:chooseGroup")
                        .build()));
        currentMessage = execute(SendMessage.builder().chatId(currentMessage.getChatId()).text(message)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build()).build());
    }

    @SneakyThrows
    private void showScheduleToday() {
        String today = LocalDate.now().getDayOfWeek().name();
        List<Lesson> lessons = getTodayLessons();
        String message = "Ось твій розклад на сьогодні \uD83D\uDE09\n";
        String day = "";
        for (Lesson lesson : lessons) {
            if(!day.equals(lesson.getDay())) {
                day = lesson.getDay();
                message += "\n\n<strong>" + day.toUpperCase() + "</strong>\n\n";
            }
            switch (lesson.getNumber()) {
                case 0 -> message += "\n0️⃣ ";
                case 1 -> message += "\n1️⃣ ";
                case 2 -> message += "\n2️⃣ ";
                case 3 -> message += "\n3️⃣ ";
                case 4 -> message += "\n4️⃣ ";
                case 5 -> message += "\n5️⃣ ";
                case 6 -> message += "\n6️⃣ ";
                case 7 -> message += "\n7️⃣ ";
                case 8 -> message += "\n8️⃣ ";
                case 9 -> message += "\n9️⃣ ";
            }
            message += lesson.getTime() + " - " + lesson.getName() + "\n";
        }
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("Menu:backToMenu")
                .build()));
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableHtml(true);
        sendMessage.setChatId(currentMessage.getChatId());
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build());
        currentMessage = execute(sendMessage);
    }

    @SneakyThrows
    private void showSchedule() {
        List<Lesson> lessons = group.getLessons();
        String message = "Ось твій розклад на тиждень \uD83D\uDE09\n";
        String day = "";
        for (Lesson lesson : lessons) {
            if(!day.equals(lesson.getDay())) {
                day = lesson.getDay();
                message += "\n\n<strong>" + day.toUpperCase() + "</strong>\n\n";
            }
            switch (lesson.getNumber()) {
                case 0 -> message += "\n0️⃣ ";
                case 1 -> message += "\n1️⃣ ";
                case 2 -> message += "\n2️⃣ ";
                case 3 -> message += "\n3️⃣ ";
                case 4 -> message += "\n4️⃣ ";
                case 5 -> message += "\n5️⃣ ";
                case 6 -> message += "\n6️⃣ ";
                case 7 -> message += "\n7️⃣ ";
                case 8 -> message += "\n8️⃣ ";
                case 9 -> message += "\n9️⃣ ";
            }
            message += lesson.getTime() + " - " + lesson.getName() + "\n";
        }
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("Menu:backToMenu")
                .build()));
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableHtml(true);
        sendMessage.setChatId(currentMessage.getChatId());
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build());
        currentMessage = execute(sendMessage);
    }

    @SneakyThrows
    private void selectNotifySettingsDay() {
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (String day : Arrays.asList("Понеділок", "Вівторок", "Середа", "Четвер", "П'ятниця")) {
            buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                    .text(day)
                    .callbackData("NotifySettingsDay:" + day)
                    .build()));
        }

        buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("Menu:backToMenu")
                .build()));

        SendMessage sendMessage = new SendMessage();
        sendMessage.enableHtml(true);
        sendMessage.setChatId(currentMessage.getChatId());
        sendMessage.setText("Налаштування якого дня будемо змінювати? \uD83E\uDD14");
        sendMessage.setReplyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build());
        currentMessage = execute(sendMessage);
    }

    @SneakyThrows
    private void showNotifySettings(String day, boolean isEdit) {
        List<Lesson> lessons = group.getLessons().stream().filter(e -> day.endsWith(e.getDay().substring(2))).toList();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (Lesson lesson : lessons) {
            String message = lesson.isNotified() ? "\uD83D\uDD14" : "\uD83D\uDD15";
            System.out.println("    " + lesson.getTime() + " " + lesson.isNotified());
            message += lesson.getTime() + " - " + lesson.getName();
            buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                    .text(message)
                    .callbackData("NotifySettings:" + lesson.getDay() + ":" + lesson.getNumber())
                    .build()));
        }
        buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                .text("Увімкнути всі \uD83D\uDD14")
                .callbackData("NotifySettings:UnmuteAll:" + day)
                .build(),
                InlineKeyboardButton.builder()
                .text("Вимкнути всі \uD83D\uDD15")
                .callbackData("NotifySettings:MuteAll:" + day)
                .build()));
        buttons.add(Arrays.asList(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("Menu:notifySettings")
                .build()));
        System.out.println(isEdit);
        if(isEdit) {
            EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
            editMessageReplyMarkup.setChatId(currentMessage.getChatId());
            editMessageReplyMarkup.setMessageId(currentMessage.getMessageId());
            editMessageReplyMarkup.setReplyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build());
            execute(editMessageReplyMarkup);
        } else {
            SendMessage sendMessage = new SendMessage();
            sendMessage.enableHtml(true);
            sendMessage.setChatId(currentMessage.getChatId());
            sendMessage.setText("Ти можеш вимкнути або увімкнути повідомлення \uD83E\uDD17");
            sendMessage.setReplyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build());
            currentMessage = execute(sendMessage);
        }
    }

    private void startThread() {
        String[] message = new String[] {
                "Привіт ✌️\nНе забудь що у тебе пара о ",
                "Я сподіваюсь ти не забув \uD83E\uDD28 що у тебе пара о ",
                "Нагадую \uD83D\uDE0C що у тебе пара о ",
                "\uD83D\uDE43 У тебе пара починається о ",
                "Наскільки я пам'ятаю \uD83E\uDD14 у тебе пара о "
        };
        currentThread = new Thread(() -> {
            LocalTime timeBuffer = LocalTime.MIN;
            String dayBuffer = "";
            while(true) {
                List<Lesson> lessons = getTodayLessons();
                LocalTime timeNow = LocalTime.of(LocalTime.now().getHour(), LocalTime.now().getMinute());
                for (Lesson lesson : lessons) {
                    if(!lesson.getDay().equals(dayBuffer)) {
                        timeBuffer = LocalTime.MIN;
                        dayBuffer = lesson.getDay();
                    }
                    if(lesson.getTime().equals(timeNow) && lesson.isNotified() && !timeBuffer.equals(lesson.getTime())) {
                        try {
                            execute(SendMessage.builder().chatId(currentMessage.getChatId())
                                    .text(message[new Random().nextInt(message.length)] + lesson.getTime() + "\n\n" +
                                    lesson.getName()).replyMarkup(addMarkupButton()).build());
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                        timeBuffer = lesson.getTime();
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        currentThread.start();
    }

    private List<Lesson> getTodayLessons() {
        String today = LocalDate.now().getDayOfWeek().name();
        return group.getLessons().stream().filter(e -> {
            switch (today) {
                case "SUNDAY" -> {
                    return e.getDay().equalsIgnoreCase("неділя");
                }
                case "MONDAY" -> {
                    return e.getDay().equalsIgnoreCase("понеділок");
                }
                case "TUESDAY" -> {
                    return e.getDay().equalsIgnoreCase("вівторок");
                }
                case "WEDNESDAY" -> {
                    return e.getDay().equalsIgnoreCase("середа");
                }
                case "THURSDAY" -> {
                    return e.getDay().equalsIgnoreCase("четвер");
                }
                case "FRIDAY" -> {
                    return e.getDay().endsWith("ятниця");
                }
                case "SATURDAY" -> {
                    return e.getDay().equalsIgnoreCase("субота");
                }
            }
            return false;
        }).toList();
    }

    @SneakyThrows
    private void sendTextMessage(String text) {
        SendMessage sendMessage = SendMessage.builder().chatId(currentMessage.getChatId()).text(text).build();
        execute(sendMessage);
    }

    @SneakyThrows
    private ReplyKeyboardMarkup addMarkupButton() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        KeyboardRow keyboardRow = new KeyboardRow();
        KeyboardButton keyboardButton = new KeyboardButton("Меню");
        keyboardRow.add(keyboardButton);
        replyKeyboardMarkup.setKeyboard(Arrays.asList(keyboardRow));
        return replyKeyboardMarkup;
    }

    @Override
    public String getBotUsername() {
        return "SchedulkaBot";
    }

    @Override
    public String getBotToken() {
        return "5521154895:AAFZY1fcZy8Diebc6d4-0sP0HjgNk-kfNxQ";
    }


    @SneakyThrows
    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        SchedulkaBot schedulkaBot = new SchedulkaBot();
        botsApi.registerBot(schedulkaBot);
    }
}
