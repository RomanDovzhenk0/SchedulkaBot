import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;

public class Lesson {
    private String day;
    private LocalTime time;
    private String name;
    private int number;
    private boolean isNotified = true;

    public Lesson(String day, LocalTime time, String name, int number) {
        this.day = day;
        this.time = time;
        this.name = name;
        this.number = number;
    }

    public String getDay() {
        return day;
    }

    public LocalTime getTime() {
        return time;
    }

    public String getName() {
        return name;
    }

    public int getNumber() { return number; }

    public boolean isNotified() {
        return isNotified;
    }

    public void changeNotified() {
        isNotified = !isNotified;
    }

    public void setNotified(boolean b) {
        isNotified = b;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "\n" + day + " : " + time + " : " + name + "\n";
    }

    @Override
    public boolean equals(Object obj) {
        Lesson lesson = (Lesson) obj;
        return day.equals(lesson.day) && time.equals(lesson.time) && name.equals(lesson.name);
    }
}
