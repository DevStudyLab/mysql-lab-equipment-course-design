package edu.lab;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class ExcelFiles {
    private ExcelFiles() {}
    public static boolean importable(LabService.Entity e){return e==LabService.Entity.DEVICE||e==LabService.Entity.BOOKING||e==LabService.Entity.MAINTENANCE;}
    public static void template(Path path,LabService.Entity entity)throws Exception {
        LabService.require(importable(entity),"该模块不支持导入");
        try(XSSFWorkbook book=new XSSFWorkbook()) {
            Sheet sheet=book.createSheet("数据");header(book,sheet,Arrays.asList(entity.fields));
            Sheet help=book.createSheet("填写说明");
            String[] notes={"第一张数据表：保留英文列名，从第2行填写数据。请勿填写公式。",
                "日期 yyyy-MM-dd；时间 yyyy-MM-dd HH:mm:ss；金额最多2位小数。",
                "设备：category_id 和 lab_id 填已有分类、实验室的数字ID；设备编号不能重复。",
                "预约：device_no 填设备编号；统一作为当前登录用户的新待审核预约，不导入审批状态。",
                "维保：maintain_type 填保养或维修；统一作为新的处理中记录，由完成维保按钮结单。",
                "整批导入在一个事务内执行：任一行失败，整批不写入。上限5000条、文件10MB。",
                "设备导入必填 device_no/device_name/category_id/lab_id；price 留空为0。",
                "维保必填 device_no/maintain_type/content/maintainer/maintain_date；cost 留空为0。",
                "预约的三个字段全部必填。时间冲突、维修中设备均会被拒绝。"};
            for(int i=0;i<notes.length;i++)help.createRow(i).createCell(0).setCellValue(notes[i]);help.setColumnWidth(0,120*256);
            save(book,path);
        }
    }
    public static void export(Path path,List<Map<String,Object>>rows)throws Exception {
        LabService.require(!rows.isEmpty(),"没有可导出的记录");
        try(XSSFWorkbook book=new XSSFWorkbook()) {
            Sheet sheet=book.createSheet("查询结果");List<String>keys=new ArrayList<>(rows.get(0).keySet());header(book,sheet,keys);
            for(int i=0;i<rows.size();i++){Row row=sheet.createRow(i+1);for(int j=0;j<keys.size();j++){
                Object val=rows.get(i).get(keys.get(j));Cell cell=row.createCell(j);
                if(val instanceof Number)cell.setCellValue(((Number)val).doubleValue());
                else if(val!=null)cell.setCellValue(display(val));
            }}
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,rows.size(),0,keys.size()-1));save(book,path);
        }
    }
    public static int importFile(Path path,LabService.Entity entity,LabService service)throws Exception {
        LabService.require(importable(entity),"该模块不支持导入");
        LabService.require(Files.size(path)<=10*1024*1024,"文件不能超过10MB");
        List<Map<String,String>> rows=new ArrayList<>();List<Integer>lineNumbers=new ArrayList<>();
        try(InputStream in=Files.newInputStream(path);Workbook book=WorkbookFactory.create(in)) {
            LabService.require(book.getNumberOfSheets()>0,"Excel中没有工作表");Sheet sheet=book.getSheetAt(0);
            Row head=sheet.getRow(0);LabService.require(head!=null,"缺少标题行");
            DataFormatter format=new DataFormatter(Locale.ROOT);
            for(int j=0;j<entity.fields.length;j++)LabService.require(entity.fields[j].equals(format.formatCellValue(head.getCell(j)).trim()),"第"+(j+1)+"列表头应为 "+entity.fields[j]+"，请使用对应的导入模板");
            LabService.require(head.getLastCellNum()==entity.fields.length,"列数与导入模板不符；查询导出文件不能直接作为导入模板");
            LabService.require(sheet.getLastRowNum()<=5000,"最多导入5000行");
            for(int i=1;i<=sheet.getLastRowNum();i++){
                Row row=sheet.getRow(i);if(row==null)continue;Map<String,String> values=new LinkedHashMap<>();boolean any=false;
                for(int j=0;j<entity.fields.length;j++){
                    Cell cell=row.getCell(j);String text="";
                    if(cell!=null) {
                        LabService.require(cell.getCellType()!=CellType.FORMULA,"第"+(i+1)+"行不允许包含公式");
                        if(cell.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(cell)){
                            LocalDateTime t=cell.getLocalDateTimeCellValue();text=entity.fields[j].endsWith("_date")?t.toLocalDate().toString():t.format(LabService.TIME);
                        } else text=format.formatCellValue(cell).trim();
                    }
                    values.put(entity.fields[j],text);any|=!text.isEmpty();
                }
                if(any){rows.add(values);lineNumbers.add(i+1);}
            }
        }
        LabService.require(!rows.isEmpty(),"没有数据行，请从第2行填写");
        return service.db.transaction(c->{
            List<String>errors=new ArrayList<>();
            for(int i=0;i<rows.size();i++){
                Savepoint point=c.setSavepoint();
                try {service.save(c,entity,null,rows.get(i));}
                catch(Exception e){c.rollback(point);errors.add("第"+lineNumbers.get(i)+"行："+Main.error(e));if(errors.size()>=30)break;}
                finally{c.releaseSavepoint(point);}
            }
            LabService.require(errors.isEmpty(),"整批导入失败，未写入任何记录：\n"+String.join("\n",errors));return rows.size();
        });
    }
    public static String display(Object value){return value instanceof Timestamp?((Timestamp)value).toLocalDateTime().format(LabService.TIME):String.valueOf(value);}
    private static void header(Workbook book,Sheet sheet,List<String>keys){
        Row row=sheet.createRow(0);CellStyle style=book.createCellStyle();Font font=book.createFont();font.setBold(true);style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for(int j=0;j<keys.size();j++){Cell c=row.createCell(j);c.setCellValue(keys.get(j));c.setCellStyle(style);sheet.setColumnWidth(j,24*256);}sheet.createFreezePane(0,1);
    }
    private static void save(Workbook book,Path path)throws IOException{
        // Write a sibling temporary file first, so a failed export cannot truncate an existing workbook.
        Path absolute=path.toAbsolutePath();Path temp=Files.createTempFile(absolute.getParent(),"lab-export-",".xlsx");
        try{try(OutputStream out=Files.newOutputStream(temp)){book.write(out);}Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
}
