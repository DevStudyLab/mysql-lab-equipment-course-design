package edu.lab;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path temp;
    static Map<String,String> values(String...pairs){Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i],pairs[i+1]);return m;}
    @Test void passwordAndDates()throws Exception{
        String hash=Passwords.hash("Demo@123");assertTrue(Passwords.verify("Demo@123",hash));assertFalse(Passwords.verify("wrong",hash));
        assertTrue(Passwords.verify("Demo@123","120000:TGFiRGVtb1NlZWRTYWx0MjAyNg==:AjbnxHhosvuwX+XzvQs4KlXSzE/6D5owfh6k5CPQPrY="));
        assertFalse(Passwords.verify("x","broken"));assertThrows(IllegalArgumentException.class,()->LabService.time("2026-02-30 10:00:00"));assertThrows(IllegalArgumentException.class,()->LabService.date("2026-02-30"));
    }
    @Test void excelTemplatesAndExport()throws Exception{
        for(LabService.Entity e:Arrays.asList(LabService.Entity.DEVICE,LabService.Entity.BOOKING,LabService.Entity.MAINTENANCE)){
            Path path=temp.resolve(e.name()+".xlsx");ExcelFiles.template(path,e);
            try(InputStream in=Files.newInputStream(path);Workbook book=WorkbookFactory.create(in)){
                assertEquals(e.fields.length,book.getSheetAt(0).getRow(0).getLastCellNum());assertEquals(e.fields[0],book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            }
        }
        Path path=temp.resolve("export.xlsx");Map<String,Object> r=new LinkedHashMap<>();r.put("device_no","=1+1");r.put("price",12.5);ExcelFiles.export(path,Collections.singletonList(r));
        try(InputStream in=Files.newInputStream(path);Workbook book=WorkbookFactory.create(in)){assertEquals(CellType.STRING,book.getSheetAt(0).getRow(1).getCell(0).getCellType());assertEquals(12.5,book.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue());}
        Path formula=temp.resolve("formula.xlsx");ExcelFiles.template(formula,LabService.Entity.DEVICE);
        Workbook parsed;try(InputStream in=Files.newInputStream(formula)){parsed=WorkbookFactory.create(in);}
        try(Workbook book=parsed){book.getSheetAt(0).createRow(1).createCell(0).setCellFormula("1+1");try(OutputStream out=Files.newOutputStream(formula)){book.write(out);}}
        assertTrue(assertThrows(IllegalArgumentException.class,()->ExcelFiles.importFile(formula,LabService.Entity.DEVICE,null)).getMessage().contains("公式"));
    }
    @Test void mysqlEndToEnd()throws Exception{
        String url=System.getenv("LAB_TEST_DB_URL");Assumptions.assumeTrue(url!=null&&!url.isEmpty(),"设置 LAB_TEST_DB_URL 为独立空测试库后运行数据库集成测试");
        Properties p=new Properties();p.setProperty("db.url",url);p.setProperty("db.user",System.getenv().getOrDefault("LAB_TEST_DB_USER","root"));p.setProperty("db.password",System.getenv().getOrDefault("LAB_TEST_DB_PASSWORD",""));
        Db db=new Db(p);db.initialize();assertThrows(IllegalArgumentException.class,db::initialize);
        LabService admin=new LabService(db,LabService.login(db,"admin","Demo@123"));
        LabService student=new LabService(db,LabService.login(db,"student1","Demo@123"));
        LabService other=new LabService(db,LabService.login(db,"student2","Demo@123"));
        assertThrows(IllegalArgumentException.class,()->LabService.login(db,"admin","wrong"));
        for(LabService.Entity e:LabService.Entity.values())assertTrue(admin.list(e,"").size()>=5,e.name());
        assertThrows(IllegalArgumentException.class,()->student.list(LabService.Entity.USER,""));
        assertThrows(IllegalArgumentException.class,()->student.save(LabService.Entity.LAB,null,values("lab_name","x")));
        int device=admin.save(LabService.Entity.DEVICE,null,values("device_no","TEST-DEVICE","device_name","集成测试仪器","category_id","1","lab_id","1","price","12.34"));
        Map<String,String> b=values("device_no","TEST-DEVICE","start_time","2027-01-01 09:00:00","end_time","2027-01-01 10:00:00");
        int booking=student.save(LabService.Entity.BOOKING,null,b);
        assertThrows(IllegalArgumentException.class,()->other.save(LabService.Entity.BOOKING,null,b));
        Map<String,String> adjacent=new LinkedHashMap<>(b);adjacent.put("start_time","2027-01-01 10:00:00");adjacent.put("end_time","2027-01-01 11:00:00");
        int next=student.save(LabService.Entity.BOOKING,null,adjacent);student.changeBooking(next,"取消");student.delete(LabService.Entity.BOOKING,next);
        assertThrows(IllegalArgumentException.class,()->other.changeBooking(booking,"取消"));
        assertThrows(IllegalArgumentException.class,()->student.approve(booking));admin.approve(booking);
        try(Connection c=db.open()) {assertEquals(0,LabService.number(Db.query(c,"SELECT COUNT(*) AS n FROM tb_usage WHERE booking_id=?",booking).get(0).get("n")));}
        assertThrows(SQLException.class,()->admin.approve(booking));
        int usage=student.save(LabService.Entity.USAGE,null,values("booking_id",""+booking,"use_start","2027-01-01 09:00:00"));
        Map<String,String> maint=values("device_no","TEST-DEVICE","maintain_type","维修","content","检查","maintainer","实验员","cost","25.00","maintain_date","2027-01-02");
        assertThrows(IllegalArgumentException.class,()->admin.save(LabService.Entity.MAINTENANCE,null,maint));
        student.save(LabService.Entity.USAGE,usage,values("booking_id",""+booking,"use_start","2027-01-01 09:00:00","use_end","2027-01-01 10:00:00"));
        assertThrows(IllegalArgumentException.class,()->other.save(LabService.Entity.USAGE,usage,values("booking_id",""+booking,"use_start","2027-01-01 09:00:00")));
        int m1=admin.save(LabService.Entity.MAINTENANCE,null,maint),m2=admin.save(LabService.Entity.MAINTENANCE,null,maint);
        assertThrows(IllegalArgumentException.class,()->student.save(LabService.Entity.USAGE,usage,values("booking_id",""+booking,"use_start","2027-01-01 09:00:00")));
        assertEquals("维修中",deviceStatus(db,device));admin.completeMaintenance(m1);assertEquals("维修中",deviceStatus(db,device));admin.completeMaintenance(m2);assertEquals("正常",deviceStatus(db,device));
        assertThrows(SQLException.class,()->admin.delete(LabService.Entity.DEVICE,device));
        Map<String,Object> stats=admin.stats(LocalDate.of(2027,1,1),LocalDate.of(2027,1,31));
        @SuppressWarnings("unchecked") List<Map<String,Object>> month=(List<Map<String,Object>>)stats.get("usage");assertEquals(1,LabService.number(month.get(0).get("usage_count")));
        Path file=temp.resolve("import.xlsx");ExcelFiles.template(file,LabService.Entity.DEVICE);
        append(file,new String[]{"IMPORT-A","导入设备","M","1","1","100.10","2026-11-01"},new String[]{"IMPORT-B","错误分类设备","M","999999","1","1","2026-11-01"});
        Exception ex=assertThrows(Exception.class,()->ExcelFiles.importFile(file,LabService.Entity.DEVICE,admin));assertTrue(ex.getMessage().contains("第3行"));assertEquals(0,admin.list(LabService.Entity.DEVICE,"IMPORT-A").size());
        ExcelFiles.template(file,LabService.Entity.DEVICE);append(file,new String[]{"IMPORT-A","导入设备","M","1","1","100.10","2026-11-01"});assertEquals(1,ExcelFiles.importFile(file,LabService.Entity.DEVICE,admin));
        Path bookingFile=temp.resolve("booking.xlsx");ExcelFiles.template(bookingFile,LabService.Entity.BOOKING);append(bookingFile,new String[]{"IMPORT-A","2027-02-01 09:00:00","2027-02-01 10:00:00"});assertEquals(1,ExcelFiles.importFile(bookingFile,LabService.Entity.BOOKING,student));
        Path maintenanceFile=temp.resolve("maintenance.xlsx");ExcelFiles.template(maintenanceFile,LabService.Entity.MAINTENANCE);append(maintenanceFile,new String[]{"IMPORT-A","保养","清洁","实验员","0","2027-02-02"});assertEquals(1,ExcelFiles.importFile(maintenanceFile,LabService.Entity.MAINTENANCE,admin));
        Path exported=temp.resolve("query.xlsx");ExcelFiles.export(exported,admin.list(LabService.Entity.BOOKING,""));assertTrue(Files.size(exported)>0);
        Map<String,String> precise=UiSmokeTest.edt(()->values("device_no","TEST-DEVICE","start_time",DateInputs.value(DateInputs.dateTime("2027-04-01 10:07:23")),"end_time",DateInputs.value(DateInputs.dateTime("2027-04-01 11:08:47"))));
        int preciseId=student.save(LabService.Entity.BOOKING,null,precise);
        try(Connection c=db.open()) {Map<String,Object>saved=Db.query(c,"SELECT start_time,end_time FROM tb_booking WHERE booking_id=?",preciseId).get(0);assertEquals(23,((Timestamp)saved.get("start_time")).toLocalDateTime().getSecond());assertEquals(47,((Timestamp)saved.get("end_time")).toLocalDateTime().getSecond());}
    }
    static String deviceStatus(Db db,int id)throws Exception{try(Connection c=db.open()){return Db.query(c,"SELECT status FROM tb_device WHERE device_id=?",id).get(0).get("status").toString();}}
    static void append(Path path,String[]...rows)throws Exception{
        Workbook book;try(InputStream in=Files.newInputStream(path)){book=WorkbookFactory.create(in);}
        try(Workbook b=book){Sheet s=b.getSheetAt(0);for(String[]values:rows){Row r=s.createRow(s.getLastRowNum()+1);for(int i=0;i<values.length;i++)r.createCell(i).setCellValue(values[i]);}try(OutputStream out=Files.newOutputStream(path)){b.write(out);}}
    }
}
