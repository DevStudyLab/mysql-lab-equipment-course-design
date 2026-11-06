package edu.lab;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class Db {
    private final Properties config;
    public Db(Properties config) { this.config = config; }
    public static Properties config() throws IOException {
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:mysql://127.0.0.1:3306/lab_equipment_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        p.setProperty("db.user", "lab_demo"); p.setProperty("db.password", "change_me");
        Path path = Paths.get("db.properties");
        if (Files.exists(path)) try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(r); }
        for (String key : p.stringPropertyNames()) {
            String env = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
            if (env != null) p.setProperty(key, env);
            if (System.getProperty(key) != null) p.setProperty(key, System.getProperty(key));
        }
        return p;
    }
    public Connection open() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", config.getProperty("db.user"));
        p.setProperty("password", config.getProperty("db.password"));
        p.setProperty("connectTimeout", "5000"); p.setProperty("socketTimeout", "15000");
        return DriverManager.getConnection(config.getProperty("db.url"), p);
    }
    public interface Work<T> { T run(Connection c) throws Exception; }
    public <T> T transaction(Work<T> work) throws Exception {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try { T result = work.run(c); c.commit(); return result; }
            catch (Exception e) { c.rollback(); throw e; }
        }
    }
    public static List<Map<String,Object>> query(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            bind(p,args);
            try (ResultSet r = p.executeQuery()) {
                List<Map<String,Object>> rows = new ArrayList<>(); ResultSetMetaData m = r.getMetaData();
                while(r.next()) { Map<String,Object> row = new LinkedHashMap<>();
                    for(int i=1;i<=m.getColumnCount();i++) row.put(m.getColumnLabel(i),m.getColumnType(i)==Types.TIMESTAMP?r.getTimestamp(i):r.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    public static int update(Connection c,String sql,Object... args) throws SQLException {
        try (PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            bind(p,args); int count=p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()) { return r.next()?r.getInt(1):count; }
        }
    }
    private static void bind(PreparedStatement p,Object[] args)throws SQLException {
        for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]);
    }
    public void initialize() throws Exception {
        try (Connection c = open()) {
            if(!query(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() LIMIT 1").isEmpty())
                throw new IllegalArgumentException("初始化只允许用于空数据库；已有数据不会被覆盖。");
            script(c,"/schema.sql"); script(c,"/seed.sql");
        }
    }
    private static void script(Connection c,String resource) throws Exception {
        try(InputStream in=Db.class.getResourceAsStream(resource)) {
            if(in==null) throw new IOException("缺少资源："+resource);
            try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); Statement s=c.createStatement()) {
                String delimiter=";", line; StringBuilder sql=new StringBuilder();
                while((line=r.readLine())!=null) {
                    String trim=line.trim();
                    if(trim.isEmpty()||trim.startsWith("--")) continue;
                    if(trim.startsWith("DELIMITER ")) { delimiter=trim.substring(10).trim(); continue; }
                    sql.append(line).append('\n');
                    if(trim.endsWith(delimiter)) {
                        String command=sql.toString().trim();
                        s.execute(command.substring(0,command.length()-delimiter.length())); sql.setLength(0);
                    }
                }
                if(sql.toString().trim().length()>0) throw new IOException("SQL 文件未结束");
            }
        }
    }
}
