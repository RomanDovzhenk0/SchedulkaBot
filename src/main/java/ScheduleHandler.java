import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressBase;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ScheduleHandler {

    private List<Course> courses = new ArrayList<>();
    private XSSFWorkbook workbook;

    public ScheduleHandler(XSSFWorkbook workbook) {
        this.workbook = workbook;
        addCourses();
    }

    public void addCourses() {
        Iterator sheetIterator = workbook.sheetIterator();
        while(sheetIterator.hasNext()) {
            XSSFSheet sheet = (XSSFSheet) sheetIterator.next();
            if(sheet.getSheetName().equalsIgnoreCase("Аудиторії")) {
                break;
            }
            Course course = new Course(sheet.getSheetName());
            addGroups(course, sheet);
            courses.add(course);
        }
    }

    public static void addGroups(Course course, XSSFSheet sheet) {
        Iterator rowIterator = sheet.rowIterator();
        while(rowIterator.hasNext()) {
            XSSFRow row = (XSSFRow) rowIterator.next();
            XSSFCell cell = row.getCell(0);

            if(cell.getStringCellValue().equalsIgnoreCase("День")) {
                Iterator cellIterator = row.cellIterator();
                cellIterator.next();
                cellIterator.next();
                cellIterator.next();
                int count = 3;
                while(cellIterator.hasNext()) {
                    Group group = new Group(((XSSFCell) cellIterator.next()).getStringCellValue());
                    addLesssons(group, sheet, count);
                    course.addGroup(group);
                    count++;
                }
                break;
            }
        }
    }

    private static void addLesssons(Group group, XSSFSheet sheet, int groupId) {
        boolean marker = false; String day, name; LocalTime time; int number;
        Iterator rowIterator = sheet.rowIterator();
        while(rowIterator.hasNext()) {
            XSSFRow row = (XSSFRow) rowIterator.next();
            try {
                day = getMergedRegionForCell(row.getCell(0), sheet).getStringCellValue();
                time = getMergedRegionForCell(row.getCell(2), sheet)
                        .getLocalDateTimeCellValue().toLocalTime();
                number = (int) getMergedRegionForCell(row.getCell(1), sheet).getNumericCellValue();
                name = getMergedRegionForCell(row.getCell(groupId), sheet).getStringCellValue();
            } catch(Exception ex) {continue;}
            if(day.equalsIgnoreCase("Понеділок")) {
                marker = true;
            }
            if(marker) {
                Lesson lesson = new Lesson(day, time, name, number);
                group.addLesson(lesson);
            }
        }
    }
    public static XSSFCell getMergedRegionForCell(XSSFCell cell, XSSFSheet sheet) {
        sheet = cell.getRow().getSheet();
        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.isInRange(cell.getRowIndex(), cell.getColumnIndex())) {
                return sheet.getRow(mergedRegion.getFirstRow()).getCell(mergedRegion.getFirstColumn());
            }
        }
        return cell;
    }

    public List<Course> getCourses() {
        return courses;
    }
}
