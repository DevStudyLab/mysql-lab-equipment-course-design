package edu.lab;

import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import com.github.lgooddatepicker.components.DatePicker;
import edu.lab.DateInputs.DateTimeInput;

class UiSmokeTest {
    private JFrame frame;
    @AfterEach void cleanup()throws Exception {if(frame!=null)edt(()->{frame.dispose();return null;});}
    @Test void loginListsFormsCharts()throws Exception {
        String url=System.getenv("LAB_UI_DB_URL");Assumptions.assumeTrue(url!=null&&!url.isEmpty()&&!GraphicsEnvironment.isHeadless(),"设置 LAB_UI_DB_URL 后运行桌面界面检查");
        Properties p=new Properties();p.setProperty("db.url",url);p.setProperty("db.user","root");p.setProperty("db.password","");
        Path images=Paths.get(System.getProperty("lab.ui.output","target/ui-smoke"));Files.createDirectories(images);
        edt(()->{Theme.install();new App(p).showLogin();for(Frame f:Frame.getFrames())if(f.isVisible()&&f instanceof JFrame)frame=(JFrame)f;assertNotNull(frame);Insets in=frame.getInsets();frame.setSize(1440+in.left+in.right,1024+in.top+in.bottom);frame.setLocation(0,0);frame.validate();return null;});
        new Robot().waitForIdle();edt(()->{Insets in=frame.getInsets();frame.setSize(1440+in.left+in.right,1024+in.top+in.bottom);frame.validate();return null;});
        capture(images.resolve("login.png"));
        edt(()->{findButton(frame,"登录").doClick();return null;});
        await(()->find(frame,JList.class)!=null);
        await(()->{JTable t=find(page(),JTable.class);return t!=null&&t.getRowCount()>=5;});
        edt(()->{find(page(),JTable.class).setRowSelectionInterval(3,3);assertControlsInside(frame.getContentPane());return null;});
        capture(images.resolve("devices.png"));
        compare(images);
        edt(()->{JTextField field=named(page(),"search-DEVICE",JTextField.class);field.setText("NO-SUCH-DEVICE-EMPTY");field.postActionEvent();return null;});
        await(()->find(page(),JTable.class).getRowCount()==0);capture(images.resolve("empty.png"));
        edt(()->{JTextField field=named(page(),"search-DEVICE",JTextField.class);field.setText("");field.postActionEvent();return null;});
        await(()->find(page(),JTable.class).getRowCount()>=5);
        edt(()->{findButton(frame,"新增设备").doClick();return null;});
        await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible())return true;return false;});
        new Robot().waitForIdle();
        edt(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible()){assertNotNull(find(w,JComboBox.class));DatePicker date=named(w,"purchase_date",DatePicker.class);assertNotNull(date);date.openPopup();assertTrue(date.isPopupOpen());date.closePopup();saveImage(((JDialog)w).getContentPane(),images.resolve("device-form.png"));for(String key:new String[]{"device_no","device_name","model","category_id","lab_id","price","purchase_date"}){JComponent input=named(w,key,JComponent.class);assertEquals(42,input.getHeight(),key+" control height");assertEquals(284,input.getWidth(),key+" control width");}w.dispose();}return null;});
        JList<?>nav=edt(()->find(frame,JList.class));assertEquals(9,edt(()->nav.getModel().getSize()));
        edt(()->{assertTrue(nav.getCellBounds(8,8).getMaxY()<=nav.getVisibleRect().getHeight(),"Navigation clipped");return null;});
        for(int i:new int[]{0,1,2,3,5,6,7,8}){final int index=i;edt(()->{nav.setSelectedIndex(index);return null;});await(()->{JTable t=find(page(),JTable.class);return t!=null&&t.getRowCount()>=5;});
            edt(()->{assertControlsInside(frame.getContentPane());return null;});
            edt(()->{JTextField field=find(page(),JTextField.class);JLabel leading=(JLabel)field.getClientProperty("JTextField.leadingComponent");assertNotNull(leading);assertEquals(12,leading.getInsets().left,"Search icon left inset");JTable t=find(page(),JTable.class);if(t.getAutoResizeMode()!=JTable.AUTO_RESIZE_OFF){JViewport viewport=(JViewport)t.getParent();assertEquals(viewport.getWidth(),t.getColumnModel().getTotalColumnWidth(),2,"Table must fill viewport");}return null;});
            if(i==1)capture(images.resolve("bookings.png"));if(i==3)capture(images.resolve("maintenance.png"));
            if(i==5)capture(images.resolve("labs.png"));
            if(i==1){edt(()->{findButton(page(),"新增预约").doClick();return null;});await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible())return true;return false;});new Robot().waitForIdle();edt(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible()){DateTimeInput start=named(w,"start_time",DateTimeInput.class),end=named(w,"end_time",DateTimeInput.class);assertNotNull(start);assertNotNull(end);assertTrue(start.getTimePicker().getEditor() instanceof JSpinner.DateEditor);assertFalse(DateInputs.value(start).isEmpty());JFormattedTextField time=((JSpinner.DateEditor)start.getTimePicker().getEditor()).getTextField();String before=time.getText();time.select(6,8);time.replaceSelection("42");assertTrue(DateInputs.value(start).endsWith(":42"));time.setText(before);start.getDatePicker().openPopup();assertTrue(start.getDatePicker().isPopupOpen());start.getDatePicker().closePopup();saveImage(((JDialog)w).getContentPane(),images.resolve("booking-form.png"));w.dispose();}return null;});}
        }
        edt(()->{nav.setSelectedIndex(4);findButton(page(),"生成统计图").doClick();return null;});
        await(()->find(page(),org.jfree.chart.ChartPanel.class)!=null);capture(images.resolve("charts.png"));
        // Verify every rendered chart has a real dataset and can paint without an exception.
        edt(()->{JPanel chartContainer=(JPanel)find(page(),org.jfree.chart.ChartPanel.class).getParent();assertEquals(4,chartContainer.getComponentCount());nav.setSelectedIndex(0);frame.setSize(1280,800);frame.validate();return null;});
        await(()->find(page(),JTable.class).getRowCount()>=5);edt(()->{assertControlsInside(frame.getContentPane());JTable t=find(page(),JTable.class);assertTrue(t.getColumnModel().getColumn(5).getWidth()>=140,"Date column truncated");return null;});capture(images.resolve("devices-1280.png"));
    }
    Component page(){JPanel pages=named(frame,"pages",JPanel.class);for(Component c:pages.getComponents())if(c.isVisible())return c;throw new AssertionError("Missing visible page");}
    void capture(Path path)throws Exception{
        edt(()->{
            Container content=frame.getContentPane();double scale=content.getGraphicsConfiguration().getDefaultTransform().getScaleX();
            BufferedImage nativeImage=new BufferedImage((int)Math.round(content.getWidth()*scale),(int)Math.round(content.getHeight()*scale),BufferedImage.TYPE_INT_RGB);Graphics2D g=nativeImage.createGraphics();g.scale(scale,scale);hints(g);content.paint(g);g.dispose();
            if(path.getFileName().toString().equals("devices.png"))ImageIO.write(nativeImage,"png",path.resolveSibling("devices-hidpi.png").toFile());
            BufferedImage logical=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);g=logical.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(nativeImage,0,0,content.getWidth(),content.getHeight(),null);g.dispose();ImageIO.write(logical,"png",path.toFile());return null;
        });
    }
    static void hints(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);}
    static void saveImage(Container content,Path path)throws Exception{BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();hints(g);content.paint(g);g.dispose();ImageIO.write(image,"png",path.toFile());}
    static void compare(Path images)throws Exception{
        Path reference=Paths.get("docs/design/reference.png");if(!Files.exists(reference))return;
        BufferedImage source=ImageIO.read(reference.toFile()),actual=ImageIO.read(images.resolve("devices.png").toFile());BufferedImage normalized=new BufferedImage(1440,1024,BufferedImage.TYPE_INT_RGB);Graphics2D n=normalized.createGraphics();n.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);n.drawImage(source,0,0,1440,1024,null);n.dispose();
        BufferedImage pair=new BufferedImage(2880,actual.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=pair.createGraphics();g.drawImage(normalized,0,0,null);g.drawImage(actual,1440,0,null);g.dispose();ImageIO.write(pair,"png",images.resolve("comparison.png").toFile());
        BufferedImage focused=new BufferedImage(2368,600,BufferedImage.TYPE_INT_RGB);g=focused.createGraphics();g.drawImage(normalized.getSubimage(256,80,1184,600),0,0,null);g.drawImage(actual.getSubimage(256,80,1184,600),1184,0,null);g.dispose();ImageIO.write(focused,"png",images.resolve("comparison-table.png").toFile());
    }
    static void assertControlsInside(Component root){if(root instanceof JButton&&root.isShowing()){Rectangle r=SwingUtilities.convertRectangle(root.getParent(),root.getBounds(),SwingUtilities.getWindowAncestor(root));assertTrue(r.x>=0&&r.y>=0&&r.x+r.width<=SwingUtilities.getWindowAncestor(root).getWidth(),"Button clipped: "+((JButton)root).getText());}if(root instanceof Container)for(Component c:((Container)root).getComponents())if(c.isVisible())assertControlsInside(c);}
    static <T extends Component>T named(Component root,String name,Class<T>type){if(name.equals(root.getName())&&type.isInstance(root))return type.cast(root);if(root instanceof Container)for(Component c:((Container)root).getComponents()){T hit=named(c,name,type);if(hit!=null)return hit;}return null;}
    static <T> T edt(Callable<T>task)throws Exception{FutureTask<T>f=new FutureTask<>(task);SwingUtilities.invokeAndWait(f);return f.get();}
    static void await(Callable<Boolean>check)throws Exception{long deadline=System.nanoTime()+15_000_000_000L;while(System.nanoTime()<deadline){if(edt(check))return;Thread.sleep(80);}fail("界面操作在15秒内未完成");}
    static <T extends Component>T find(Component root,Class<T>type){if(type.isInstance(root))return type.cast(root);if(root instanceof Container)for(Component c:((Container)root).getComponents()){T hit=find(c,type);if(hit!=null)return hit;}return null;}
    static JButton findButton(Component root,String text){if(root instanceof JButton&&text.equals(((JButton)root).getText()))return(JButton)root;if(root instanceof Container)for(Component c:((Container)root).getComponents()){JButton hit=findButton(c,text);if(hit!=null)return hit;}return null;}
}
