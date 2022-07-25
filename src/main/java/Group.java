import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Group {
    private String name;
    private List<Lesson> lessons = new ArrayList<>();

    public Group(String name) {
        this.name = name;
    }

    public void addLesson(Lesson lesson) {
        if(!lesson.getName().equals("") && !lessons.contains(lesson)) {
            if(lessons.stream().anyMatch(e -> e.getTime().equals(lesson.getTime()) && e.getDay().equals(lesson.getDay()))) {
                for(Lesson temp : lessons) {
                    if(temp.getDay().equals(lesson.getDay()) && temp.getTime().equals(lesson.getTime())) {
                        temp.setName(temp.getName() + "\n | " + lesson.getName());
                    }
                }
            } else {
                lessons.add(lesson);
            }
        }
    }

    public String getName() {
        return name;
    }

    public List<Lesson> getLessons() {
        return lessons;
    }

    @Override
    public String toString() {
        return "Група " + name + '\n' + lessons;
    }
}
