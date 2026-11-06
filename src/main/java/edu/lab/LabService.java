package edu.lab;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class LabService {
    public static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    public static final class User {
        public final int id; public final String name,role;
        User(int id,String name,String role) { this.id=id;this.name=name;this.role=role; }
        public boolean staff() {return !"USER".equals(role);}
        public boolean admin() {return "ADMIN".equals(role);}
    }
    public enum Entity {
        DEVICE("设备台账","tb_device","device_id",new String[]{"device_no","device_name","model","category_id","lab_id","price","purchase_date"}),
        LAB("实验室","tb_lab","lab_id",new String[]{"lab_name","location","capacity","manager"}),
        CATEGORY("设备分类","tb_device_category","category_id",new String[]{"category_name","description"}),
        USER("用户","tb_user","user_id",new String[]{"username","password","real_name","role","phone"}),
        BOOKING("预约","tb_booking","booking_id",new String[]{"device_no","start_time","end_time"}),
        USAGE("使用记录","tb_usage","usage_id",new String[]{"booking_id","use_start","use_end"}),
        MAINTENANCE("维保","tb_maintenance","maintain_id",new String[]{"device_no","maintain_type","content","maintainer","cost","maintain_date"}),
        LOG("操作日志","tb_log","log_id",new String[0]);
        public final String title,table,id; public final String[] fields;
        Entity(String title,String table,String id,String[] fields) {this.title=title;this.table=table;this.id=id;this.fields=fields;}
        @Override public String toString(){return title;}
    }
    public final Db db;
    private final User user;
    public LabService(Db db,User user){this.db=db;this.user=user;}
    public User user(){return user;}
    public static User login(Db db,String username,String password) throws Exception {
        try(Connection c=db.open()) {
            List<Map<String,Object>> rows=Db.query(c,"SELECT * FROM tb_user WHERE username=?",username.trim());
            if(rows.isEmpty()||!Passwords.verify(password,rows.get(0).get("password").toString()))
                throw new IllegalArgumentException("用户名或密码错误");
            Map<String,Object> r=rows.get(0);
            User u=new User(number(r.get("user_id")),r.get("real_name").toString(),r.get("role").toString());
            Db.update(c,"INSERT INTO tb_log(user_id,action) VALUES(?,?)",u.id,"用户登录"); return u;
        }
    }
    public List<Map<String,Object>> list(Entity e,String search) throws Exception {
        if((e==Entity.USER||e==Entity.LOG)&&!user.admin()) throw new IllegalArgumentException("仅管理员可以查看");
        String source;
        switch(e) {
            case DEVICE: source="SELECT d.*,c.category_name,l.lab_name,v.occupancy FROM tb_device d JOIN tb_device_category c ON d.category_id=c.category_id JOIN tb_lab l ON d.lab_id=l.lab_id JOIN v_device_status v ON d.device_id=v.device_id";break;
            case BOOKING: source="SELECT * FROM v_booking_detail";break;
            case USAGE: source="SELECT * FROM v_usage_detail";break;
            case MAINTENANCE: source="SELECT * FROM v_maintenance_summary";break;
            case USER: source="SELECT user_id,username,real_name,role,phone FROM tb_user";break;
            default: source="SELECT * FROM "+e.table;
        }
        List<Map<String,Object>> rows;
        try(Connection c=db.open()) {
            if(!user.staff()&&(e==Entity.BOOKING||e==Entity.USAGE))
                rows=Db.query(c,source+" WHERE user_id=? ORDER BY "+e.id+" DESC",user.id);
            else rows=Db.query(c,source+" ORDER BY "+e.id+" DESC");
        }
        String term=search==null?"":search.trim().toLowerCase(Locale.ROOT);
        if(!term.isEmpty()) rows.removeIf(r->r.values().stream().noneMatch(v->v!=null&&v.toString().toLowerCase(Locale.ROOT).contains(term)));
        return rows;
    }
    public List<Map<String,Object>> choices(String kind) throws Exception {
        String sql;
        switch(kind) {
            case "lab_id":sql="SELECT lab_id AS id,lab_name AS name FROM tb_lab ORDER BY lab_id";break;
            case "category_id":sql="SELECT category_id AS id,category_name AS name FROM tb_device_category ORDER BY category_id";break;
            case "device_no":sql="SELECT device_no AS id,CONCAT(device_no,' ',device_name,' [',status,']') AS name FROM tb_device ORDER BY device_no";break;
            default:throw new IllegalArgumentException("未知选项");
        }
        try(Connection c=db.open()){return Db.query(c,sql);}
    }
    public int save(Entity e,Integer id,Map<String,String> values) throws Exception {
        return db.transaction(c->save(c,e,id,values));
    }
    int save(Connection c,Entity e,Integer id,Map<String,String> v) throws Exception {
        if(e==Entity.BOOKING)return booking(c,id,v);
        if(e==Entity.USAGE)return usage(c,id,v);
        if(e==Entity.MAINTENANCE)return maintenance(c,id,v);
        staff(); if(e==Entity.LOG)throw new IllegalArgumentException("日志不可编辑");
        if(e==Entity.USER&&!user.admin())throw new IllegalArgumentException("仅管理员可管理用户");
        List<String> fields=new ArrayList<>(Arrays.asList(e.fields)); List<Object> data=new ArrayList<>();
        for(String f:new ArrayList<>(fields)) {
            String raw=value(v,f); Object val;
            switch(f) {
                case "password":
                    if(id!=null&&raw.isEmpty()){fields.remove(f);continue;}
                    require(raw.length()>=8,"密码至少8位"); val=Passwords.hash(raw);break;
                case "role": require(Arrays.asList("ADMIN","STAFF","USER").contains(raw),"角色不合法");val=raw;break;
                case "price":val=money(raw);break;
                case "capacity":val=positive(raw,"容纳人数");break;
                case "category_id":case "lab_id":val=positive(raw,f);break;
                case "purchase_date":val=raw.isEmpty()?null:date(raw);break;
                case "model":case "description":case "phone":case "manager":val=raw;break;
                default:val=required(v,f);
            }
            data.add(val);
        }
        if(e==Entity.USER&&id!=null) {
            Map<String,Object> existing=row(c,"SELECT * FROM tb_user WHERE user_id=?",id);
            if("ADMIN".equals(existing.get("role"))&&!"ADMIN".equals(value(v,"role"))) {
                require(id!=user.id,"不能降低当前登录管理员的角色");
                require(number(Db.query(c,"SELECT COUNT(*) AS n FROM tb_user WHERE role='ADMIN'").get(0).get("n"))>1,"至少保留一位管理员");
            }
        }
        int result=write(c,e,id,fields,data); log(c,e.title+(id==null?"新增 #":"修改 #")+result);return result;
    }
    private int booking(Connection c,Integer id,Map<String,String> v)throws Exception {
        Timestamp start=time(required(v,"start_time")),end=time(required(v,"end_time"));
        require(start.before(end),"预约结束时间必须晚于开始时间");
        int device=device(c,required(v,"device_no"));
        row(c,"SELECT device_id FROM tb_device WHERE device_id=? FOR UPDATE",device);
        require("正常".equals(row(c,"SELECT status FROM tb_device WHERE device_id=?",device).get("status")),"设备维修中，不能预约");
        if(id!=null){Map<String,Object> b=row(c,"SELECT * FROM tb_booking WHERE booking_id=?",id);owner(b);require("待审核".equals(b.get("status")),"只能修改待审核预约");}
        try(CallableStatement p=c.prepareCall("{call sp_check_time_conflict(?,?,?,?,?)}")){
            p.setInt(1,device);p.setTimestamp(2,start);p.setTimestamp(3,end);p.setInt(4,id==null?0:id);p.registerOutParameter(5,Types.INTEGER);p.execute();
            require(p.getInt(5)==0,"预约时间冲突，请选择其他时间段");
        }
        int result;
        if(id==null) result=Db.update(c,"INSERT INTO tb_booking(user_id,device_id,start_time,end_time) VALUES(?,?,?,?)",user.id,device,start,end);
        else {Db.update(c,"UPDATE tb_booking SET device_id=?,start_time=?,end_time=? WHERE booking_id=?",device,start,end,id);result=id;}
        log(c,"预约保存 #"+result);return result;
    }
    private int usage(Connection c,Integer id,Map<String,String> v)throws Exception {
        Timestamp start=time(required(v,"use_start"));String endText=value(v,"use_end");Timestamp end=endText.isEmpty()?null:time(endText);
        require(end==null||end.after(start),"使用结束时间必须晚于开始时间");
        int booking=positive(required(v,"booking_id"),"预约ID");
        Map<String,Object>b=row(c,"SELECT * FROM tb_booking WHERE booking_id=? FOR UPDATE",booking); owner(b);
        if(id==null) {
            require("通过".equals(b.get("status")),"只有审批通过的预约才能登记使用");
            require("正常".equals(row(c,"SELECT status FROM tb_device WHERE device_id=?",b.get("device_id")).get("status")),"设备维修中");
        } else {
            Map<String,Object>x=row(c,"SELECT * FROM tb_usage WHERE usage_id=?",id);
            require(number(x.get("booking_id"))==booking,"不能改变使用记录的关联预约");
            if(end==null)require("正常".equals(row(c,"SELECT status FROM tb_device WHERE device_id=?",b.get("device_id")).get("status")),"设备维修中，不能恢复为使用中");
        }
        require(!start.before((Timestamp)b.get("start_time"))&&start.before((Timestamp)b.get("end_time")),"实际开始时间应在预约时段内");
        int result;
        if(id==null) {
            result=Db.update(c,"INSERT INTO tb_usage(booking_id,use_start,use_end) VALUES(?,?,?)",booking,start,end);
            Db.update(c,"UPDATE tb_booking SET status='已使用' WHERE booking_id=?",booking);
        }else{Db.update(c,"UPDATE tb_usage SET use_start=?,use_end=? WHERE usage_id=?",start,end,id);result=id;}
        log(c,"使用登记 #"+result);return result;
    }
    private int maintenance(Connection c,Integer id,Map<String,String>v)throws Exception {
        staff();int device=device(c,required(v,"device_no"));String type=required(v,"maintain_type");
        require(Arrays.asList("保养","维修").contains(type),"维保类型不合法");
        if(id==null)require(Db.query(c,"SELECT x.usage_id FROM tb_usage x JOIN tb_booking b ON x.booking_id=b.booking_id WHERE b.device_id=? AND x.use_end IS NULL",device).isEmpty(),"设备正在使用，请先结束使用");
        if(id!=null){Map<String,Object>m=row(c,"SELECT * FROM tb_maintenance WHERE maintain_id=?",id);require("处理中".equals(m.get("status")),"已完成维保不能修改");require(number(m.get("device_id"))==device,"维保记录不能更换设备");}
        int result=write(c,Entity.MAINTENANCE,id,Arrays.asList("device_id","maintain_type","content","maintainer","cost","maintain_date"),
            Arrays.asList(device,type,required(v,"content"),required(v,"maintainer"),money(value(v,"cost")),date(required(v,"maintain_date"))));
        log(c,"维保保存 #"+result);return result;
    }
    public void approve(int id)throws Exception {
        staff();db.transaction(c->{try(CallableStatement p=c.prepareCall("{call sp_approve_booking(?,?)}")){p.setInt(1,id);p.setInt(2,user.id);p.execute();}log(c,"预约通过 #"+id);return null;});
    }
    public void changeBooking(int id,String status)throws Exception {
        require(Arrays.asList("取消","驳回").contains(status),"状态不合法");
        db.transaction(c->{Map<String,Object>b=row(c,"SELECT * FROM tb_booking WHERE booking_id=? FOR UPDATE",id);
            if("驳回".equals(status))staff();else owner(b);
            require(Arrays.asList("待审核","通过").contains(b.get("status")),"该预约不能取消或驳回");
            Db.update(c,"UPDATE tb_booking SET status=?,approver_id=? WHERE booking_id=?",status,user.staff()?user.id:b.get("approver_id"),id);log(c,"预约"+status+" #"+id);return null;});
    }
    public void completeMaintenance(int id)throws Exception {
        staff();db.transaction(c->{Map<String,Object>m=row(c,"SELECT * FROM tb_maintenance WHERE maintain_id=? FOR UPDATE",id);require("处理中".equals(m.get("status")),"维保已经完成");
            Db.update(c,"UPDATE tb_maintenance SET status='已完成',completed_at=NOW() WHERE maintain_id=?",id);log(c,"维保完成 #"+id);return null;});
    }
    public void delete(Entity e,int id)throws Exception {
        db.transaction(c->{
            if(e==Entity.LOG)throw new IllegalArgumentException("操作日志不允许删除");
            if(e==Entity.BOOKING){Map<String,Object>b=row(c,"SELECT * FROM tb_booking WHERE booking_id=?",id);owner(b);require(Arrays.asList("待审核","驳回","取消").contains(b.get("status")),"仅可删除待审核、驳回或取消的预约");}
            else {staff();if(e==Entity.USER){require(user.admin(),"仅管理员可删除用户");require(id!=user.id,"不能删除当前登录用户");}
                if(e==Entity.MAINTENANCE)require("已完成".equals(row(c,"SELECT status FROM tb_maintenance WHERE maintain_id=?",id).get("status")),"请先完成维保");
                if(e==Entity.USAGE){Map<String,Object>x=row(c,"SELECT * FROM tb_usage WHERE usage_id=?",id);Db.update(c,"DELETE FROM tb_usage WHERE usage_id=?",id);Db.update(c,"UPDATE tb_booking SET status='通过' WHERE booking_id=?",x.get("booking_id"));log(c,"删除使用记录 #"+id);return null;}
            }
            require(Db.update(c,"DELETE FROM "+e.table+" WHERE "+e.id+"=?",id)>0,"记录不存在");log(c,e.title+"删除 #"+id);return null;
        });
    }
    public Map<String,Object> stats(LocalDate from,LocalDate to)throws Exception {
        require(!from.isAfter(to),"开始日期不得晚于结束日期");Map<String,Object> out=new LinkedHashMap<>();
        try(Connection c=db.open()) {
            Timestamp a=Timestamp.valueOf(from.atStartOfDay()),b=Timestamp.valueOf(to.plusDays(1).atStartOfDay());
            out.put("usage",Db.query(c,"CALL sp_monthly_usage_stat(?,?)",a,b));
            out.put("maintenance",Db.query(c,"SELECT DATE_FORMAT(maintain_date,'%Y-%m') AS month,COUNT(*) AS maintenance_count FROM tb_maintenance WHERE maintain_date>=? AND maintain_date<? GROUP BY month ORDER BY month",java.sql.Date.valueOf(from),java.sql.Date.valueOf(to.plusDays(1))));
            out.put("categories",Db.query(c,"SELECT c.category_name,COUNT(d.device_id) AS device_count FROM tb_device_category c LEFT JOIN tb_device d ON c.category_id=d.category_id GROUP BY c.category_id,c.category_name"));
            // shortcut: utilization uses 24-hour calendar availability; add opening-hours calendars for real deployment.
            out.put("labs",Db.query(c,"SELECT l.lab_name,COUNT(DISTINCT d.device_id) AS devices,COALESCE(SUM(GREATEST(0,TIMESTAMPDIFF(SECOND,GREATEST(x.use_start,?),LEAST(x.use_end,?))))/3600,0) AS used_hours FROM tb_lab l LEFT JOIN tb_device d ON l.lab_id=d.lab_id LEFT JOIN tb_booking bk ON d.device_id=bk.device_id LEFT JOIN tb_usage x ON bk.booking_id=x.booking_id AND x.use_end IS NOT NULL AND x.use_start<? AND x.use_end>? GROUP BY l.lab_id,l.lab_name",a,b,b,a));
            out.put("days",java.time.temporal.ChronoUnit.DAYS.between(from,to)+1);
        }return out;
    }
    private int write(Connection c,Entity e,Integer id,List<String> fields,List<Object> data)throws SQLException {
        if(id==null)return Db.update(c,"INSERT INTO "+e.table+"("+String.join(",",fields)+") VALUES("+String.join(",",Collections.nCopies(fields.size(),"?"))+")",data.toArray());
        List<String> sets=new ArrayList<>();for(String f:fields)sets.add(f+"=?");List<Object> args=new ArrayList<>(data);args.add(id);
        Db.update(c,"UPDATE "+e.table+" SET "+String.join(",",sets)+" WHERE "+e.id+"=?",args.toArray());return id;
    }
    private void staff(){require(user.staff(),"仅管理员或实验员可执行此操作");}
    private void owner(Map<String,Object>b){require(user.staff()||number(b.get("user_id"))==user.id,"只能操作自己的预约和使用记录");}
    private void log(Connection c,String action)throws SQLException{Db.update(c,"INSERT INTO tb_log(user_id,action) VALUES(?,?)",user.id,action);}
    private static Map<String,Object> row(Connection c,String sql,Object...args)throws SQLException {List<Map<String,Object>>r=Db.query(c,sql,args);require(!r.isEmpty(),"记录不存在");return r.get(0);}
    private static int device(Connection c,String no)throws SQLException{return number(row(c,"SELECT device_id FROM tb_device WHERE device_no=?",no).get("device_id"));}
    public static int number(Object n){return ((Number)n).intValue();}
    public static String value(Map<String,String>v,String k){return v.getOrDefault(k,"").trim();}
    private static String required(Map<String,String>v,String k){String s=value(v,k);require(!s.isEmpty(),"必填项不能为空："+k);return s;}
    private static int positive(String s,String field){try{int i=Integer.parseInt(s);require(i>0,field+"必须为正整数");return i;}catch(NumberFormatException e){throw new IllegalArgumentException(field+"必须为正整数");}}
    private static BigDecimal money(String s){try{BigDecimal d=new BigDecimal(s.isEmpty()?"0":s);require(d.signum()>=0&&d.scale()<=2&&d.compareTo(new BigDecimal("99999999.99"))<=0,"金额须为0至99999999.99，最多两位小数");return d;}catch(NumberFormatException e){throw new IllegalArgumentException("金额格式错误");}}
    public static Timestamp time(String s){try{return Timestamp.valueOf(LocalDateTime.parse(s,TIME));}catch(Exception e){throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd HH:mm:ss");}}
    public static java.sql.Date date(String s){try{return java.sql.Date.valueOf(LocalDate.parse(s));}catch(Exception e){throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd");}}
    public static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
