package edu.lab;

import javax.swing.*;
import java.sql.SQLException;
import java.util.Properties;

public final class Main {
    private Main() {}
    public static void main(String[] args)throws Exception {
        Properties config=Db.config();
        if(args.length>0&&"--init".equals(args[0])){new Db(config).initialize();System.out.println("数据库初始化完成。演示账号 admin / staff / student1，密码均为 Demo@123");return;}
        if(args.length>0&&"--check".equals(args[0])){
            Db db=new Db(config);LabService.User user=LabService.login(db,"admin","Demo@123");
            LabService s=new LabService(db,user);
            for(LabService.Entity e:LabService.Entity.values())System.out.println(e.title+": "+s.list(e,"").size());
            System.out.println("连接、登录、全部列表查询正常。");return;
        }
        Theme.install();
        SwingUtilities.invokeLater(()->new App(config).showLogin());
    }
    public static String error(Throwable e){
        if(e instanceof SQLException){SQLException s=(SQLException)e;
            switch(s.getErrorCode()){
                case 1062:return "编号、名称或关联预约重复，请检查输入";
                case 1451:return "记录已被其他数据引用，不能删除";
                case 1452:return "关联的用户、分类、实验室或设备不存在";
                case 1406:return "输入内容过长，请缩短后重试";
                case 1045:return "数据库用户名或密码错误";
                case 1049:return "数据库不存在，请先按README创建空数据库";
                case 1146:case 1305:return "数据库尚未完整初始化，请使用空数据库初始化";
                case 1644:
                    if(s.getMessage().contains("time conflicts"))return "预约时间冲突";
                    if(s.getMessage().contains("not pending"))return "预约已经处理，不能重复审批";
                    if(s.getMessage().contains("under maintenance"))return "设备正在维保";
                    return "业务操作被数据库拒绝："+s.getMessage();
                default:if(s.getSQLState()!=null&&s.getSQLState().startsWith("08"))return "无法连接MySQL，请检查地址、端口及服务状态";
            }
        }
        return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    }
}
