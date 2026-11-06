package edu.lab;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.text.NumberFormat;
import org.jfree.chart.*;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import com.github.lgooddatepicker.components.DatePicker;
import edu.lab.DateInputs.DateTimeInput;

public final class App {
    private final JFrame frame=new JFrame("高校实验室设备共享预约与维保管理系统");
    private final Properties config;
    private final JLabel status=new JLabel("就绪");
    private LabService service;
    private boolean mutating=false;
    private static final Map<String,String> LABELS=new HashMap<>();
    static {
        String[][] labels={{"device_id","设备ID"},{"device_no","设备编号"},{"device_name","设备名称"},{"model","型号"},{"category_id","分类ID"},{"lab_id","实验室ID"},{"price","价格"},{"purchase_date","购入日期"},{"category_name","设备分类"},{"description","说明"},{"lab_name","实验室名称"},{"location","所在位置"},{"capacity","容纳人数"},{"manager","负责人"},{"status","状态"},{"occupancy","当前占用"},{"user_id","用户ID"},{"username","用户名"},{"password","密码（修改时留空不变）"},{"real_name","姓名"},{"role","角色"},{"phone","联系方式"},{"booking_id","预约ID"},{"start_time","预约开始"},{"end_time","预约结束"},{"approver_id","审批人ID"},{"approver_name","审批人"},{"usage_id","使用ID"},{"use_start","实际开始"},{"use_end","实际结束（可空）"},{"duration_minutes","时长（分钟）"},{"maintain_id","维保ID"},{"maintain_type","维保类型"},{"content","维保内容"},{"maintainer","维保人员"},{"cost","费用"},{"maintain_date","维保日期"},{"completed_at","完成时间"},{"log_id","日志ID"},{"action","操作"},{"created_at","发生时间"}};
        for(String[]p:labels)LABELS.put(p[0],p[1]);
    }
    private static String title(LabService.Entity e){switch(e){case BOOKING:return "预约管理";case MAINTENANCE:return "维保管理";case CATEGORY:return "设备分类";case USER:return "用户管理";default:return e.title;}}
    private static String subtitle(LabService.Entity e){switch(e){case DEVICE:return "设备档案、归属与运行状态。";case BOOKING:return "管理预约时段与审批进度。";case USAGE:return "登记实际使用，追溯设备使用记录。";case MAINTENANCE:return "跟进维修保养，保持设备良好状态。";case LAB:return "维护实验室及负责人信息。";case CATEGORY:return "统一设备分类，让台账更有条理。";case USER:return "维护账号、角色与联系方式。";default:return "查看系统操作与数据变更记录。";}}
    private static String createText(LabService.Entity e){switch(e){case DEVICE:return "新增设备";case BOOKING:return "新增预约";case USAGE:return "登记使用";case MAINTENANCE:return "新增维保";case CATEGORY:return "新增分类";default:return "新增"+e.title;}}
    private static String navIcon(String title){switch(title){case "设备台账":return "stack";case "预约管理":return "calendar-event";case "使用记录":return "journal-text";case "维保管理":return "wrench-adjustable";case "统计图表":return "bar-chart-line";case "实验室":return "buildings";case "设备分类":return "grid";case "用户管理":return "person";default:return "clipboard-check";}}
    private static JPanel pageHeading(String title,String subtitle){
        JPanel heading=Theme.panel(new BorderLayout(16,0),Theme.BG),text=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0),Theme.BG);
        text.add(Theme.label(title,34,true,Theme.INK));JLabel description=Theme.label(subtitle,16,false,Theme.MUTED);description.setBorder(Theme.padding(14,22,0,0));text.add(description);heading.add(text,BorderLayout.CENTER);heading.setBorder(Theme.padding(0,0,28,0));return heading;
    }
    private static List<String> columns(LabService.Entity e){
        switch(e){
            case DEVICE:return Arrays.asList("device_no","device_name","lab_name","category_name","status","purchase_date","price");
            case BOOKING:return Arrays.asList("booking_id","device_name","real_name","start_time","end_time","status","approver_name");
            case USAGE:return Arrays.asList("usage_id","booking_id","device_name","real_name","use_start","use_end","duration_minutes");
            case MAINTENANCE:return Arrays.asList("maintain_id","device_name","maintain_type","content","maintainer","cost","maintain_date","status","completed_at");
            case USER:return Arrays.asList("user_id","username","real_name","role","phone");
            case LAB:return Arrays.asList("lab_id","lab_name","location","capacity","manager");
            case CATEGORY:return Arrays.asList("category_id","category_name","description");
            default:return Arrays.asList("log_id","user_id","action","created_at");
        }
    }
    private void details(Map<String,Object> record){
        JPanel form=Theme.panel(new GridBagLayout(),Color.WHITE);form.setBorder(Theme.padding(18,20,18,20));int row=0;
        for(Map.Entry<String,Object>entry:record.entrySet()){JTextField value=new JTextField(entry.getValue()==null?"":ExcelFiles.display(entry.getValue()),28);value.setEditable(false);addField(form,row++,LABELS.getOrDefault(entry.getKey(),entry.getKey()),value);}
        JScrollPane scroll=new JScrollPane(form);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setPreferredSize(new Dimension(580,Math.min(680,record.size()*56+48)));JOptionPane.showMessageDialog(frame,scroll,"完整记录",JOptionPane.PLAIN_MESSAGE);
    }
    public App(Properties config){this.config=config;frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setMinimumSize(new Dimension(1280,800));frame.setSize(1440,1024);frame.setLocationRelativeTo(null);status.setFont(Theme.BODY);status.setForeground(Theme.MUTED);}
    public void showLogin(){
        JPanel outer=Theme.panel(new BorderLayout(),Color.WHITE);
        JPanel brand=Theme.panel(new BorderLayout(),Theme.NAV);brand.setPreferredSize(new Dimension(460,0));brand.setBorder(Theme.padding(60,48,44,48));
        brand.add(Theme.label("LAB  /  实验室设备管理",21,true,Color.WHITE),BorderLayout.NORTH);
        JPanel intro=Theme.panel(new GridLayout(0,1,0,18),Theme.NAV);intro.add(Theme.label("让设备管理",36,true,Color.WHITE));intro.add(Theme.label("清晰、有序。",36,true,Color.WHITE));intro.add(Theme.label("设备预约 · 使用登记 · 维修保养",15,false,new Color(0xA9B9D0)));
        JPanel introCenter=Theme.panel(new GridBagLayout(),Theme.NAV);introCenter.add(intro);brand.add(introCenter,BorderLayout.CENTER);brand.add(Theme.label("高校实验室设备共享预约与维保管理系统",13,false,new Color(0xA9B9D0)),BorderLayout.SOUTH);outer.add(brand,BorderLayout.WEST);
        JPanel right=Theme.panel(new GridBagLayout(),Color.WHITE),form=Theme.panel(new GridBagLayout(),Color.WHITE);form.setBorder(Theme.padding(28,32,28,32));
        JTextField url=new JTextField(config.getProperty("db.url"),55),dbuser=new JTextField(config.getProperty("db.user"));
        JPasswordField dbpass=new JPasswordField(config.getProperty("db.password")),password=new JPasswordField("Demo@123");JTextField username=new JTextField("admin");
        username.setColumns(26);Theme.leadingIcon(username,"person");password.putClientProperty("JTextField.showRevealButton",true);
        GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=0;g.gridwidth=2;g.anchor=GridBagConstraints.WEST;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(0,8,12,8);form.add(Theme.label("欢迎回来",30,true,Theme.INK),g);
        g.gridy=1;g.insets=new Insets(0,8,28,8);form.add(Theme.label("登录，开始管理实验室设备。",14,false,Theme.MUTED),g);
        int r=2;addField(form,r++,"用户名",username);addField(form,r++,"密码",password);
        JButton login=Theme.button("登录","box-arrow-right",true),init=Theme.button("初始化空数据库","database",false),connection=Theme.button("数据库连接设置","gear",false);
        g.gridy=r++;g.insets=new Insets(24,8,12,8);form.add(login,g);
        JLabel hint=Theme.label("演示账号 admin / staff / student1，密码 Demo@123",12,false,Theme.MUTED);g.gridy=r++;g.insets=new Insets(0,8,18,8);form.add(hint,g);
        JPanel buttons=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0),Color.WHITE);buttons.add(connection);g.gridy=r;form.add(buttons,g);right.add(form);outer.add(right,BorderLayout.CENTER);
        connection.addActionListener(e->{if(mutating)return;JPanel settingsForm=Theme.panel(new GridBagLayout(),Color.WHITE);settingsForm.setBorder(Theme.padding(16,16,16,16));addField(settingsForm,0,"MySQL JDBC地址",url);addField(settingsForm,1,"数据库账号",dbuser);addField(settingsForm,2,"数据库密码",dbpass);GridBagConstraints sg=new GridBagConstraints();sg.gridx=0;sg.gridy=3;sg.gridwidth=2;sg.insets=new Insets(16,8,4,8);sg.anchor=GridBagConstraints.WEST;settingsForm.add(init,sg);JOptionPane.showMessageDialog(frame,settingsForm,"数据库连接设置 · 关闭后本次生效",JOptionPane.PLAIN_MESSAGE);});
        Runnable settings=()->{config.setProperty("db.url",url.getText().trim());config.setProperty("db.user",dbuser.getText().trim());config.setProperty("db.password",new String(dbpass.getPassword()));};
        login.addActionListener(e->{if(mutating)return;settings.run();String name=username.getText(),pw=new String(password.getPassword());Db db=new Db(config);
            mutate(()->LabService.login(db,name,pw),u->{service=new LabService(db,u);showMain();});});
        init.addActionListener(e->{if(mutating)return;if(JOptionPane.showConfirmDialog(frame,"仅初始化指定的空数据库，加入演示数据。确认已创建空数据库？","初始化",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            settings.run();mutate(()->{new Db(config).initialize();return true;},ok->JOptionPane.showMessageDialog(frame,"初始化完成，可以登录。"));});
        frame.setContentPane(outer);frame.getRootPane().setDefaultButton(login);frame.setVisible(true);frame.revalidate();
    }
    private void showMain(){
        JPanel root=Theme.panel(new BorderLayout(),Theme.BG),workspace=Theme.panel(new BorderLayout(),Theme.BG),sidebar=Theme.panel(new BorderLayout(),Theme.NAV);
        sidebar.setPreferredSize(new Dimension(256,0));JPanel brand=Theme.panel(new BorderLayout(16,0),Theme.NAV);brand.setBorder(Theme.padding(28,28,28,20));
        brand.add(Theme.label("LAB",32,true,Color.WHITE),BorderLayout.WEST);brand.add(Theme.label("<html>高校实验室<br>设备共享管理</html>",16,true,Color.WHITE),BorderLayout.CENTER);sidebar.add(brand,BorderLayout.NORTH);
        CardLayout layout=new CardLayout();JPanel pages=Theme.panel(layout,Theme.BG);pages.setName("pages");LinkedHashMap<String,JPanel>views=new LinkedHashMap<>();
        LabService.Entity[]order={LabService.Entity.DEVICE,LabService.Entity.BOOKING,LabService.Entity.USAGE,LabService.Entity.MAINTENANCE,LabService.Entity.LAB,LabService.Entity.CATEGORY,LabService.Entity.USER,LabService.Entity.LOG};
        for(int i=0;i<order.length;i++){
            if(i==4)views.put("统计图表",charts());LabService.Entity e=order[i];
            if(!service.user().admin()&&(e==LabService.Entity.USER||e==LabService.Entity.LOG))continue;
            views.put(title(e),new TablePanel(e));
        }
        views.forEach(pages::add);
        JList<String>nav=new JList<>(views.keySet().toArray(new String[0]));nav.setName("navigation");nav.setBackground(Theme.NAV);nav.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);nav.setBorder(Theme.padding(12,10,10,10));
        nav.setCellRenderer((list,value,index,selected,focus)->{
            JPanel cell=Theme.panel(new BorderLayout(),Theme.NAV);cell.setBorder(index==5?BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(0x49586E)),Theme.padding(20,0,4,0)):Theme.padding(4,0,4,0));
            JButton label=Theme.button(value,null,false);label.setFont(Theme.BODY.deriveFont(Font.BOLD,18));label.setForeground(Color.WHITE);label.setHorizontalAlignment(SwingConstants.LEFT);label.setIcon(Theme.icon(navIcon(value),Color.WHITE,21));label.setIconTextGap(18);label.setBackground(selected?Theme.BLUE:Theme.NAV);label.setBorderPainted(false);label.setMargin(new Insets(6,18,6,12));label.setPreferredSize(new Dimension(220,52));cell.add(label);return cell;
        });
        nav.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&nav.getSelectedValue()!=null){String key=nav.getSelectedValue();layout.show(pages,key);JPanel p=views.get(key);if(p instanceof TablePanel)((TablePanel)p).refresh();}});
        JScrollPane navScroll=new JScrollPane(nav);navScroll.setBorder(BorderFactory.createEmptyBorder());navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);navScroll.getViewport().setBackground(Theme.NAV);sidebar.add(navScroll,BorderLayout.CENTER);JLabel sideNote=Theme.label("LABORATORY WORKSPACE",10,false,new Color(0x7E91AE));sideNote.setBorder(Theme.padding(20,28,24,12));sidebar.add(sideNote,BorderLayout.SOUTH);root.add(sidebar,BorderLayout.WEST);
        JPanel top=Theme.panel(new BorderLayout(),Color.WHITE);top.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.LINE),Theme.padding(12,32,12,24)));top.setPreferredSize(new Dimension(0,66));top.add(Theme.label("高校实验室设备共享管理系统",18,true,Theme.INK),BorderLayout.WEST);
        JPanel account=Theme.panel(new FlowLayout(FlowLayout.RIGHT,14,0),Color.WHITE);JLabel who=Theme.label(service.user().name,14,true,Theme.INK);who.setIcon(Theme.icon("person-circle",Theme.BLUE,28));who.setIconTextGap(10);account.add(who);
        JButton logout=Theme.button("退出登录","box-arrow-right",false);logout.setBorderPainted(false);logout.addActionListener(e->{if(!mutating){service=null;showLogin();}});account.add(logout);top.add(account,BorderLayout.EAST);workspace.add(top,BorderLayout.NORTH);workspace.add(pages,BorderLayout.CENTER);
        JPanel footer=Theme.panel(new BorderLayout(),Theme.BG);footer.setBorder(Theme.padding(14,32,18,32));footer.add(status,BorderLayout.WEST);JLabel connected=Theme.label("已连接数据库",12,false,Theme.MUTED);connected.setIcon(Theme.icon("database",Theme.MUTED,17));connected.setIconTextGap(8);footer.add(connected,BorderLayout.EAST);workspace.add(footer,BorderLayout.SOUTH);root.add(workspace,BorderLayout.CENTER);
        frame.getRootPane().setDefaultButton(null);frame.setContentPane(root);frame.revalidate();frame.repaint();nav.setSelectedIndex(0);
    }
    private final class TablePanel extends JPanel {
        final LabService.Entity entity;
        final JTable table=new JTable();final JTextField search=new JTextField(24);
        final CardLayout resultLayout=new CardLayout();final JPanel result=Theme.panel(resultLayout,Color.WHITE);
        List<String> columnKeys=new ArrayList<>();
        int preferredTableWidth;
        List<Map<String,Object>> rows=new ArrayList<>();int generation=0;
        TablePanel(LabService.Entity entity){
            super(new BorderLayout());this.entity=entity;setBackground(Theme.BG);setBorder(Theme.padding(32,32,0,32));
            JPanel heading=Theme.panel(new BorderLayout(0,12),Theme.BG),actions=Theme.panel(new FlowLayout(FlowLayout.RIGHT,8,0),Theme.BG),workflow=Theme.panel(new FlowLayout(FlowLayout.LEFT,8,0),Theme.BG);
            boolean edit=entity!=LabService.Entity.LOG&&(service.user().staff()||entity==LabService.Entity.BOOKING||entity==LabService.Entity.USAGE);
            JPanel titleRow=pageHeading(title(entity),subtitle(entity));
            if(edit){JButton create=Theme.button(createText(entity),"plus-lg",true);create.addActionListener(e->{if(!mutating)openForm(entity,null,Collections.emptyMap(),this::refresh);});titleRow.add(create,BorderLayout.EAST);
                button(actions,"修改",()->selected(row->openForm(entity,LabService.number(row.get(entity.id)),row,this::refresh)));
                if(entity!=LabService.Entity.USAGE||service.user().staff())button(actions,"删除",()->selected(row->{if(JOptionPane.showConfirmDialog(frame,"删除选中记录？关联中的基础数据会拒绝删除。","确认删除",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)mutate(()->{service.delete(entity,LabService.number(row.get(entity.id)));return true;},ok->refresh());}));}
            if(entity==LabService.Entity.BOOKING){
                if(service.user().staff()){button(workflow,"审批通过",()->selected(row->mutate(()->{service.approve(LabService.number(row.get(entity.id)));return true;},ok->refresh())));button(workflow,"驳回",()->bookingStatus("驳回"));}
                button(workflow,"取消预约",()->bookingStatus("取消"));
                button(workflow,"登记使用",()->selected(row->{Map<String,Object>initial=new LinkedHashMap<>();initial.put("booking_id",row.get("booking_id"));initial.put("use_start",row.get("start_time"));openForm(LabService.Entity.USAGE,null,initial,this::refresh);}));
            }
            if(entity==LabService.Entity.USAGE)button(workflow,"结束使用",()->selected(row->{Map<String,Object>copy=new LinkedHashMap<>(row);copy.put("use_end",LocalDateTime.now().format(LabService.TIME));openForm(entity,LabService.number(row.get(entity.id)),copy,this::refresh);}));
            if(entity==LabService.Entity.MAINTENANCE&&service.user().staff())button(workflow,"完成维保",()->selected(row->mutate(()->{service.completeMaintenance(LabService.number(row.get(entity.id)));return true;},ok->refresh())));
            if(ExcelFiles.importable(entity)&&edit)button(actions,"Excel导入",()->{Path path=choose(false,null);if(path!=null)mutate(()->ExcelFiles.importFile(path,entity,service),count->{info("成功导入 "+count+" 行");refresh();});});
            button(actions,"Excel导出",()->{Path path=choose(true,entity.title+"查询结果.xlsx");if(path!=null){List<Map<String,Object>>snapshot=new ArrayList<>(rows);mutate(()->{ExcelFiles.export(path,snapshot);return true;},ok->info("已导出当前查询结果"));}});
            if(ExcelFiles.importable(entity)&&edit)button(actions,"下载模板",()->{Path path=choose(true,entity.title+"导入模板.xlsx");if(path!=null)mutate(()->{ExcelFiles.template(path,entity);return true;},ok->info("模板已保存"));});
            JPanel find=Theme.panel(new BorderLayout(8,0),Theme.BG);find.setPreferredSize(new Dimension(316,42));search.setColumns(17);search.setName("search-"+entity.name());search.putClientProperty("JTextField.placeholderText",entity==LabService.Entity.DEVICE?"搜索编号、名称、型号":"输入关键词搜索");Theme.leadingIcon(search,"search");search.getAccessibleContext().setAccessibleName("关键词搜索");Theme.field(search);find.add(search,BorderLayout.CENTER);
            JButton refresh=Theme.button("","arrow-repeat",false);refresh.setToolTipText("查询 / 刷新（也可按回车）");refresh.getAccessibleContext().setAccessibleName("查询 / 刷新");refresh.addActionListener(e->{if(!mutating)refresh();});find.add(refresh,BorderLayout.EAST);search.addActionListener(e->refresh());
            JPanel tools=Theme.panel(new BorderLayout(16,0),Theme.BG);tools.add(find,BorderLayout.CENTER);tools.add(actions,BorderLayout.EAST);heading.add(titleRow,BorderLayout.NORTH);heading.add(tools,BorderLayout.CENTER);if(workflow.getComponentCount()>0)heading.add(workflow,BorderLayout.SOUTH);heading.setBorder(Theme.padding(0,0,24,0));add(heading,BorderLayout.NORTH);
            table.setName(entity.name()+"Table");table.setAutoResizeMode(entity==LabService.Entity.DEVICE?JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS:JTable.AUTO_RESIZE_OFF);table.setRowHeight(entity==LabService.Entity.DEVICE?68:52);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);table.getTableHeader().setReorderingAllowed(false);table.getTableHeader().setPreferredSize(new Dimension(0,50));table.setFont(Theme.BODY.deriveFont(17f));table.setGridColor(Theme.LINE);table.setShowVerticalLines(false);table.setShowHorizontalLines(true);table.setIntercellSpacing(new Dimension(0,1));table.setFillsViewportHeight(true);
            table.setDefaultRenderer(Object.class,new DataCell());table.setDefaultRenderer(Number.class,new DataCell());table.setDefaultRenderer(java.sql.Timestamp.class,new DataCell());table.setDefaultRenderer(java.sql.Date.class,new DataCell());
            table.setToolTipText("双击记录查看完整字段");table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2&&table.rowAtPoint(e.getPoint())>=0)selected(App.this::details);}});
            table.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"details");table.getActionMap().put("details",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){selected(App.this::details);}});
            DefaultTableCellRenderer header=(DefaultTableCellRenderer)table.getTableHeader().getDefaultRenderer();header.setHorizontalAlignment(SwingConstants.LEFT);
            table.getTableHeader().setDefaultRenderer((t,value,selected,focus,row,col)->{Component cell=header.getTableCellRendererComponent(t,value,selected,focus,row,col);((JComponent)cell).setBorder(Theme.padding(0,16,0,16));return cell;});
            JScrollPane scroll=new JScrollPane(table);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getViewport().setBackground(Color.WHITE);
            scroll.getViewport().addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){fitTable();}});
            result.add(scroll,"table");
            JPanel empty=Theme.panel(new GridBagLayout(),Color.WHITE);JLabel emptyText=Theme.label("<html><center>暂无匹配记录<br><br><span style='font-size:12px;color:#697589'>试试其他关键词，或新增一条记录。</span></center></html>",18,true,Theme.MUTED);empty.add(emptyText);result.add(empty,"empty");add(result,BorderLayout.CENTER);
            JLabel detailHint=Theme.label("双击记录查看完整字段",11,false,Theme.MUTED);detailHint.setBorder(Theme.padding(8,0,0,0));add(detailHint,BorderLayout.SOUTH);
        }
        void bookingStatus(String state){selected(row->mutate(()->{service.changeBooking(LabService.number(row.get(entity.id)),state);return true;},ok->refresh()));}
        void fitTable(){Container parent=table.getParent();if(parent instanceof JViewport&&parent.getWidth()>0)table.setAutoResizeMode(parent.getWidth()>=preferredTableWidth?JTable.AUTO_RESIZE_ALL_COLUMNS:JTable.AUTO_RESIZE_OFF);}
        void selected(Consumer<Map<String,Object>>action){if(mutating)return;int row=table.getSelectedRow();if(row<0){info("请先选择一条记录");return;}action.accept(rows.get(table.convertRowIndexToModel(row)));}
        void refresh(){
            if(service==null)return;int ticket=++generation;String term=search.getText();LabService snapshot=service;
            background(()->snapshot.list(entity,term),data->{if(ticket!=generation||snapshot!=service)return;rows=data;
                List<String>keys=columns(entity);columnKeys=keys;
                Object[][] cells=new Object[data.size()][keys.size()];for(int i=0;i<data.size();i++)for(int j=0;j<keys.size();j++)cells[i][j]=data.get(i).get(keys.get(j));
                String[]heads=keys.stream().map(k->entity==LabService.Entity.DEVICE&&k.equals("lab_name")?"所属实验室":entity==LabService.Entity.DEVICE&&k.equals("status")?"设备状态":entity==LabService.Entity.DEVICE&&k.equals("category_name")?"分类":LABELS.getOrDefault(k,k)).toArray(String[]::new);
                table.setModel(new DefaultTableModel(cells,heads){@Override public boolean isCellEditable(int r,int c){return false;}@Override public Class<?>getColumnClass(int col){for(int i=0;i<getRowCount();i++){Object v=getValueAt(i,col);if(v!=null)return v.getClass();}return Object.class;}});
                for(int j=0;j<keys.size();j++){String key=keys.get(j);int width=key.equals("device_name")||key.equals("lab_name")?180:key.contains("time")||key.contains("start")||key.contains("end")||key.equals("completed_at")?180:key.equals("price")||key.equals("cost")||key.equals("purchase_date")?145:key.equals("device_no")?132:120;table.getColumnModel().getColumn(j).setPreferredWidth(width);table.getColumnModel().getColumn(j).setMinWidth(entity==LabService.Entity.DEVICE?(key.equals("purchase_date")||key.equals("price")?140:key.equals("device_no")?132:112):70);}
                preferredTableWidth=0;for(int j=0;j<keys.size();j++)preferredTableWidth+=table.getColumnModel().getColumn(j).getPreferredWidth();fitTable();
                table.getRowSorter().setSortKeys(Collections.singletonList(new RowSorter.SortKey(0,SortOrder.ASCENDING)));resultLayout.show(result,data.isEmpty()?"empty":"table");status.setText("共 "+rows.size()+" 条"+entity.title+"记录");
            });
        }
        private final class DataCell extends DefaultTableCellRenderer {
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
                super.getTableCellRendererComponent(t,value,selected,false,row,col);setFont(Theme.BODY.deriveFont(17f));setBorder(Theme.padding(0,16,0,16));setHorizontalAlignment(SwingConstants.LEFT);setIcon(null);setForeground(Theme.INK);setText(value==null?"":ExcelFiles.display(value));
                String key=columnKeys.get(t.convertColumnIndexToModel(col));
                if(entity==LabService.Entity.DEVICE&&key.equals("device_name")){Map<String,Object>r=rows.get(t.convertRowIndexToModel(row));setText("<html><b>"+Theme.html(value)+"</b><br><span style='font-size:12px;color:#697589'>"+Theme.html(r.get("model"))+"</span></html>");}
                if((key.equals("price")||key.equals("cost"))&&value instanceof Number){setText(NumberFormat.getCurrencyInstance(Locale.CHINA).format(value));setHorizontalAlignment(SwingConstants.RIGHT);}
                if(key.equals("status")){String state=String.valueOf(value);Color color=Arrays.asList("正常","通过","已使用","已完成").contains(state)?new Color(0x22A447):Arrays.asList("驳回","取消").contains(state)?new Color(0xD94C58):new Color(0xD89813);setIcon(Theme.icon("circle-fill",color,9));setIconTextGap(9);}
                return this;
            }
        }
    }
    private static final class Choice {
        final String id,label;Choice(Object id,Object label){this.id=String.valueOf(id);this.label=String.valueOf(label);}@Override public String toString(){return label;}
    }
    private void openForm(LabService.Entity entity,Integer id,Map<String,Object>record,Runnable after){
        if(mutating)return;
        LabService snapshot=service;
        background(()->{
            Map<String,List<Map<String,Object>>> choices=new HashMap<>();
            for(String f:entity.fields)if(Arrays.asList("category_id","lab_id","device_no").contains(f)&&!(entity==LabService.Entity.DEVICE&&f.equals("device_no")))choices.put(f,snapshot.choices(f));
            return choices;
        },choices->{
            if(snapshot!=service)return;
            String dialogTitle=id==null?createText(entity):"修改"+(entity==LabService.Entity.DEVICE?"设备":entity.title);int columns=entity==LabService.Entity.DEVICE?2:1;
            JDialog dialog=new JDialog(frame,dialogTitle,true);JPanel form=Theme.panel(new GridBagLayout(),Color.WHITE);form.setBorder(Theme.padding(28,28,24,28));Map<String,JComponent> inputs=new LinkedHashMap<>();int r=0;
            JPanel formTitle=Theme.panel(new BorderLayout(0,8),Color.WHITE);formTitle.add(Theme.label(dialogTitle,24,true,Theme.INK),BorderLayout.NORTH);formTitle.add(Theme.label(subtitle(entity),13,false,Theme.MUTED),BorderLayout.SOUTH);
            GridBagConstraints titleG=new GridBagConstraints();titleG.gridx=0;titleG.gridy=0;titleG.gridwidth=columns;titleG.weightx=1;titleG.fill=GridBagConstraints.HORIZONTAL;titleG.insets=new Insets(0,0,28,0);form.add(formTitle,titleG);
            for(String f:entity.fields){
                Object existing=record.get(f);String val=existing==null?"":ExcelFiles.display(existing);JComponent input;
                if(id==null&&val.isEmpty()){
                    if(f.equals("start_time")||f.equals("use_start"))val=LocalDateTime.now().withNano(0).format(LabService.TIME);
                    if(f.equals("end_time"))val=LocalDateTime.now().plusHours(1).withNano(0).format(LabService.TIME);
                    if(f.endsWith("_date"))val=LocalDate.now().toString();if(f.equals("price")||f.equals("cost"))val="0";
                    if(f.equals("maintainer"))val=service.user().name;
                }
                if(choices.containsKey(f)){
                    JComboBox<Choice> combo=new JComboBox<>();for(Map<String,Object>option:choices.get(f))combo.addItem(new Choice(option.get("id"),option.get("name")));
                    for(int i=0;i<combo.getItemCount();i++)if(combo.getItemAt(i).id.equals(val))combo.setSelectedIndex(i);input=combo;
                } else if(f.equals("role")) {JComboBox<String>combo=new JComboBox<>(new String[]{"USER","STAFF","ADMIN"});if(!val.isEmpty())combo.setSelectedItem(val);input=combo;}
                else if(f.equals("maintain_type")){JComboBox<String>combo=new JComboBox<>(new String[]{"保养","维修"});if(!val.isEmpty())combo.setSelectedItem(val);input=combo;}
                else if(f.endsWith("_date"))input=DateInputs.date(val);
                else if(Arrays.asList("start_time","end_time","use_start","use_end").contains(f))input=DateInputs.dateTime(val);
                else if(f.equals("password"))input=new JPasswordField();
                else input=new JTextField(val);
                if(id!=null&&((entity==LabService.Entity.USAGE&&f.equals("booking_id"))||(entity==LabService.Entity.MAINTENANCE&&f.equals("device_no"))))input.setEnabled(false);
                input.setName(f);Theme.field(input);input.setPreferredSize(new Dimension(columns==2?284:420,42));inputs.put(f,input);
                JPanel field=Theme.panel(new BorderLayout(0,8),Color.WHITE);JLabel label=Theme.label(f.equals("category_id")?"设备分类":f.equals("lab_id")?"所属实验室":LABELS.getOrDefault(f,f),14,false,Theme.MUTED);label.setLabelFor(input);field.add(label,BorderLayout.NORTH);field.add(input,BorderLayout.CENTER);
                GridBagConstraints fg=new GridBagConstraints();fg.gridx=r%columns;fg.gridy=1+r/columns;fg.weightx=1;fg.fill=GridBagConstraints.HORIZONTAL;fg.anchor=GridBagConstraints.NORTH;fg.insets=new Insets(0,0,24,columns==2&&r%columns==0?24:0);form.add(field,fg);r++;
            }
            String help=entity==LabService.Entity.USER?"角色：ADMIN 管理员 / STAFF 实验员 / USER 普通用户":entity==LabService.Entity.DEVICE?"设备编号需唯一；日期格式 yyyy-MM-dd。":entity==LabService.Entity.BOOKING||entity==LabService.Entity.USAGE?"选中时、分或秒后输入，或使用上下键调整。":"日期格式 yyyy-MM-dd";
            GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=1+(r+columns-1)/columns;g.gridwidth=columns;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(0,0,24,0);form.add(Theme.label(help,12,false,Theme.MUTED),g);
            JPanel buttons=Theme.panel(new FlowLayout(FlowLayout.RIGHT,12,0),Color.WHITE);buttons.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,Theme.LINE),Theme.padding(20,0,0,0)));JButton save=Theme.button("保存","check2",true),cancel=Theme.button("取消",null,false);buttons.add(cancel);buttons.add(save);g.gridy++;g.insets=new Insets(0,0,0,0);form.add(buttons,g);cancel.addActionListener(e->{if(!mutating)dialog.dispose();});
            save.addActionListener(e->{if(mutating)return;Map<String,String>values=new LinkedHashMap<>();try{inputs.forEach((f,input)->{
                if(input instanceof JComboBox){Object selected=((JComboBox<?>)input).getSelectedItem();values.put(f,selected instanceof Choice?((Choice)selected).id:selected==null?"":selected.toString());}
                else if(input instanceof JPasswordField)values.put(f,new String(((JPasswordField)input).getPassword()));
                else if(input instanceof DatePicker||input instanceof DateTimeInput)values.put(f,DateInputs.value(input));
                else values.put(f,((JTextField)input).getText());
            });}catch(IllegalArgumentException invalid){showError(invalid);return;}save.setEnabled(false);mutate(()->service.save(entity,id,values),result->{dialog.dispose();after.run();},()->save.setEnabled(true));});
            // Repack after peer creation, which can update font metrics on a high-DPI monitor.
            dialog.setContentPane(form);dialog.pack();dialog.pack();dialog.setLocationRelativeTo(frame);dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);dialog.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(!mutating)dialog.dispose();}});dialog.getRootPane().setDefaultButton(save);dialog.setVisible(true);
        });
    }
    private JPanel charts(){
        JPanel panel=Theme.panel(new BorderLayout(0,24),Theme.BG);panel.setBorder(Theme.padding(32,32,0,32));JPanel heading=Theme.panel(new BorderLayout(),Theme.BG);heading.add(pageHeading("统计图表","从使用与维保记录了解设备运行情况。"),BorderLayout.NORTH);
        JPanel controls=Theme.panel(new FlowLayout(FlowLayout.LEFT,10,0),Theme.BG);DatePicker from=DateInputs.date(LocalDate.now().minusMonths(5).withDayOfMonth(1).toString()),to=DateInputs.date(LocalDate.now().toString());from.setName("stats-from");to.setName("stats-to");
        Theme.field(from);Theme.field(to);controls.add(new JLabel("开始日期"));controls.add(from);controls.add(new JLabel("结束日期"));controls.add(to);
        JPanel content=Theme.panel(new GridLayout(2,2,16,16),Theme.BG);heading.add(controls,BorderLayout.CENTER);panel.add(heading,BorderLayout.NORTH);panel.add(content,BorderLayout.CENTER);
        button(controls,"生成统计图",()->{try{String start=DateInputs.value(from),end=DateInputs.value(to);LabService.require(!start.isEmpty()&&!end.isEmpty(),"请选择统计的开始和结束日期");LocalDate a=LocalDate.parse(start),b=LocalDate.parse(end);LabService snapshot=service;background(()->snapshot.stats(a,b),data->{if(snapshot==service)drawCharts(content,data);});}catch(Exception e){showError(e);}});
        panel.add(Theme.label("使用次数按实际开始统计；利用率按已结束使用时长与自然日计算，跨期时长按日期裁剪。",11,false,Theme.MUTED),BorderLayout.SOUTH);return panel;
    }
    @SuppressWarnings("unchecked") private void drawCharts(JPanel target,Map<String,Object>data){
        target.removeAll();DefaultCategoryDataset usage=new DefaultCategoryDataset(),maint=new DefaultCategoryDataset(),labs=new DefaultCategoryDataset();DefaultPieDataset<String>categories=new DefaultPieDataset<>();
        for(Map<String,Object>r:(List<Map<String,Object>>)data.get("usage"))usage.addValue((Number)r.get("usage_count"),"使用次数",r.get("month").toString());
        for(Map<String,Object>r:(List<Map<String,Object>>)data.get("maintenance"))maint.addValue((Number)r.get("maintenance_count"),"维保数量",r.get("month").toString());
        for(Map<String,Object>r:(List<Map<String,Object>>)data.get("categories"))categories.setValue(r.get("category_name").toString(),(Number)r.get("device_count"));
        for(Map<String,Object>r:(List<Map<String,Object>>)data.get("labs")){double count=((Number)r.get("devices")).doubleValue(),hours=((Number)r.get("used_hours")).doubleValue();labs.addValue(count==0?0:hours/(count*((Number)data.get("days")).doubleValue()*24)*100,"利用率 %",r.get("lab_name").toString());}
        ChartFactory.setChartTheme(org.jfree.chart.StandardChartTheme.createJFreeTheme());
        JFreeChart[]charts={ChartFactory.createLineChart("设备月度使用次数","月份","次数",usage,PlotOrientation.VERTICAL,false,true,false),ChartFactory.createLineChart("维保月度趋势","月份","数量",maint,PlotOrientation.VERTICAL,false,true,false),ChartFactory.createBarChart("实验室设备利用率","实验室","%",labs,PlotOrientation.VERTICAL,false,true,false),ChartFactory.createPieChart("设备分类分布",categories,true,true,false)};
        org.jfree.chart.StandardChartTheme theme=new org.jfree.chart.StandardChartTheme("中文");Font font=Theme.BODY.deriveFont(12f);theme.setExtraLargeFont(font.deriveFont(Font.BOLD,17));theme.setLargeFont(font);theme.setRegularFont(font);theme.setSmallFont(font);theme.setChartBackgroundPaint(Color.WHITE);theme.setPlotBackgroundPaint(Color.WHITE);theme.setDomainGridlinePaint(Theme.LINE);theme.setRangeGridlinePaint(Theme.LINE);theme.setShadowVisible(false);theme.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());theme.setDrawingSupplier(new org.jfree.chart.plot.DefaultDrawingSupplier(new Paint[]{Theme.BLUE,new Color(0x16A68A),new Color(0x8B5CF6),new Color(0xF0AE30),new Color(0xE66B80)},org.jfree.chart.plot.DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE,org.jfree.chart.plot.DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,org.jfree.chart.plot.DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE,org.jfree.chart.plot.DefaultDrawingSupplier.DEFAULT_SHAPE_SEQUENCE));
        for(JFreeChart chart:charts){theme.apply(chart);chart.getPlot().setOutlineVisible(false);if(chart.getPlot() instanceof org.jfree.chart.plot.PiePlot){org.jfree.chart.plot.PiePlot<?>pie=(org.jfree.chart.plot.PiePlot<?>)chart.getPlot();pie.setShadowPaint(null);pie.setLabelBackgroundPaint(null);pie.setLabelOutlinePaint(null);pie.setLabelShadowPaint(null);}if(chart.getPlot() instanceof org.jfree.chart.plot.CategoryPlot&&chart==charts[2])chart.getCategoryPlot().getDomainAxis().setCategoryLabelPositions(org.jfree.chart.axis.CategoryLabelPositions.UP_45);ChartPanel chartPanel=new ChartPanel(chart);chartPanel.setBorder(Theme.padding(16,16,16,16));chartPanel.setBackground(Color.WHITE);target.add(chartPanel);}target.revalidate();target.repaint();status.setText("统计图已更新");
    }
    private Path choose(boolean save,String name){
        JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel工作簿 (*.xlsx, *.xls)","xlsx","xls"));
        if(name!=null)chooser.setSelectedFile(new java.io.File(name));int result=save?chooser.showSaveDialog(frame):chooser.showOpenDialog(frame);if(result!=JFileChooser.APPROVE_OPTION)return null;
        Path path=chooser.getSelectedFile().toPath();if(save&&!path.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))path=Paths.get(path+".xlsx");
        if(save&&Files.exists(path)&&JOptionPane.showConfirmDialog(frame,"文件已存在，覆盖？","确认",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;return path;
    }
    private static void addField(JPanel p,int row,String label,JComponent input){Theme.field(input);GridBagConstraints g=new GridBagConstraints();g.gridy=row;g.gridx=0;g.anchor=GridBagConstraints.WEST;g.insets=new Insets(8,8,8,16);JLabel l=Theme.label(label,15,false,Theme.MUTED);l.setLabelFor(input);p.add(l,g);g.gridx=1;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(8,0,8,8);p.add(input,g);}
    private void button(JPanel p,String text,Runnable action){String icon=text.equals("修改")?"pencil":text.equals("删除")?"trash3":text.equals("Excel导入")?"file-earmark-arrow-up":text.equals("Excel导出")?"file-earmark-arrow-down":text.equals("下载模板")?"download":text.equals("生成统计图")?"bar-chart-line":null;JButton b=Theme.button(text,icon,text.equals("生成统计图"));b.addActionListener(e->{if(!mutating)action.run();});p.add(b);}
    private void info(String text){JOptionPane.showMessageDialog(frame,text);}
    private void showError(Throwable e){JTextArea text=new JTextArea(Main.error(e),Math.min(15,Main.error(e).split("\n").length+2),65);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);JOptionPane.showMessageDialog(frame,new JScrollPane(text),"操作未完成",JOptionPane.WARNING_MESSAGE);}
    private <T> void background(Callable<T> action,Consumer<T> done){background(action,done,()->{});}
    private <T> void background(Callable<T> action,Consumer<T> done,Runnable finish){
        status.setText("正在处理…");new SwingWorker<T,Void>(){
            @Override protected T doInBackground()throws Exception{return action.call();}
            @Override protected void done(){try{done.accept(get());if("正在处理…".equals(status.getText()))status.setText("就绪");}catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();status.setText("操作未完成");showError(cause);}finally{finish.run();}}
        }.execute();
    }
    private <T> void mutate(Callable<T> action,Consumer<T> done){mutate(action,done,()->{});}
    private <T> void mutate(Callable<T> action,Consumer<T> done,Runnable finish){
        if(mutating)return;mutating=true;frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        background(action,result->{status.setText("操作成功");done.accept(result);},()->{mutating=false;frame.setCursor(Cursor.getDefaultCursor());finish.run();});
    }
}
