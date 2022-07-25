import java.util.ArrayList;
import java.util.List;

class Course {
    private String name;
    private List<Group> groups = new ArrayList<>();

    public Course(String name) {
        this.name = name;
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

    public String getName() {
        return name;
    }

    public List<Group> getGroups() {
        return groups;
    }
}
