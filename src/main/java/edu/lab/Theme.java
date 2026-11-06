package edu.lab;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

final class Theme {
    static final Color NAV=new Color(0x172438), BLUE=new Color(0x2167FA), BG=new Color(0xF3F6FB);
    static final Color INK=new Color(0x172338), MUTED=new Color(0x697589), LINE=new Color(0xE1E7EF);
    static final Font BODY=new Font("Microsoft YaHei",Font.PLAIN,16);
    private Theme() {}
    static void install() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont",BODY);
        UIManager.put("Panel.background",BG);
        UIManager.put("Label.foreground",INK);
        UIManager.put("Component.arc",10);
        UIManager.put("Button.arc",10);
        UIManager.put("TextComponent.arc",10);
        UIManager.put("Component.focusWidth",1);
        UIManager.put("Component.focusColor",new Color(0xAFCBFF));
        UIManager.put("Component.borderColor",new Color(0xCCD6E3));
        UIManager.put("Button.background",Color.WHITE);
        UIManager.put("Button.foreground",INK);
        UIManager.put("Button.hoverBackground",new Color(0xEDF3FC));
        UIManager.put("Button.default.background",BLUE);
        UIManager.put("Button.default.foreground",Color.WHITE);
        UIManager.put("Button.default.hoverBackground",new Color(0x1558DC));
        UIManager.put("Button.minimumHeight",40);
        UIManager.put("TextField.minimumHeight",40);
        UIManager.put("PasswordField.minimumHeight",40);
        UIManager.put("ComboBox.minimumHeight",40);
        UIManager.put("Table.background",Color.WHITE);
        UIManager.put("Table.foreground",INK);
        UIManager.put("Table.selectionBackground",new Color(0xDFEBFF));
        UIManager.put("Table.selectionForeground",INK);
        UIManager.put("Table.selectionInactiveBackground",new Color(0xDFEBFF));
        UIManager.put("Table.selectionInactiveForeground",INK);
        UIManager.put("TableHeader.background",new Color(0xE6ECF4));
        UIManager.put("TableHeader.foreground",INK);
        UIManager.put("TableHeader.font",BODY.deriveFont(Font.BOLD,16));
        UIManager.put("TableHeader.cellMargins",new Insets(0,16,0,16));
        UIManager.put("ScrollBar.width",10);
        UIManager.put("TabbedPane.tabHeight",42);
        UIManager.put("OptionPane.background",Color.WHITE);
    }
    static JLabel label(String text,int size,boolean bold,Color color) {
        JLabel l=new JLabel(text);l.setFont(BODY.deriveFont(bold?Font.BOLD:Font.PLAIN,(float)size));l.setForeground(color);return l;
    }
    static JPanel panel(LayoutManager layout,Color color) {JPanel p=new JPanel(layout);p.setBackground(color);return p;}
    static Border padding(int top,int left,int bottom,int right){return BorderFactory.createEmptyBorder(top,left,bottom,right);}
    static Icon icon(String name,Color color,int size) {
        FlatSVGIcon icon=new FlatSVGIcon("icons/"+name+".svg",size,size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c->color));return icon;
    }
    static JButton button(String text,String icon,boolean primary) {
        JButton b=new JButton(text);b.setFont(BODY.deriveFont(15f));b.setIconTextGap(8);b.setMargin(new Insets(4,14,4,14));
        if(primary){b.setBackground(BLUE);b.setForeground(Color.WHITE);b.putClientProperty("JButton.buttonType","default");b.putClientProperty("JButton.defaultButtonFollowsFocus",false);}
        if(icon!=null)b.setIcon(icon(icon,primary?Color.WHITE:INK,18));b.setPreferredSize(new Dimension(text.isEmpty()?42:b.getPreferredSize().width,42));return b;
    }
    static void field(JComponent input){Dimension size=input.getPreferredSize();input.setPreferredSize(new Dimension(size.width,42));if(input instanceof JTextField)((JTextField)input).setMargin(new Insets(0,10,0,10));}
    static void leadingIcon(JTextField field,String name){JLabel leading=new JLabel(icon(name,MUTED,18));leading.setBorder(padding(0,12,0,8));field.putClientProperty("JTextField.leadingComponent",leading);}
    static String html(Object value) {return value==null?"":ExcelFiles.display(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
