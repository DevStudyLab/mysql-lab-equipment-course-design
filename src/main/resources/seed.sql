-- Demo accounts only. All five passwords: Demo@123 (PBKDF2 SHA-256).
INSERT INTO tb_user(username,password,real_name,role,phone) VALUES
 ('admin','120000:TGFiRGVtb1NlZWRTYWx0MjAyNg==:AjbnxHhosvuwX+XzvQs4KlXSzE/6D5owfh6k5CPQPrY=','管理员','ADMIN',''),
 ('staff','120000:TGFiRGVtb1NlZWRTYWx0MjAyNg==:AjbnxHhosvuwX+XzvQs4KlXSzE/6D5owfh6k5CPQPrY=','实验员','STAFF',''),
 ('student1','120000:TGFiRGVtb1NlZWRTYWx0MjAyNg==:AjbnxHhosvuwX+XzvQs4KlXSzE/6D5owfh6k5CPQPrY=','同学甲','USER',''),
 ('student2','120000:TGFiRGVtb1NlZWRTYWx0MjAyNg==:AjbnxHhosvuwX+XzvQs4KlXSzE/6D5owfh6k5CPQPrY=','同学乙','USER',''),
 ('student3','120000:TGFiRGVtb1NlZWRTYWx0MjAyNg==:AjbnxHhosvuwX+XzvQs4KlXSzE/6D5owfh6k5CPQPrY=','同学丙','USER','');
INSERT INTO tb_lab(lab_name,location,capacity,manager) VALUES
 ('电子技术实验室','实验楼101',30,'张老师'),('物理实验室','实验楼102',30,'李老师'),
 ('网络实验室','实验楼201',40,'王老师'),('机械实验室','实验楼202',20,'陈老师'),('综合实验室','实验楼301',40,'周老师');
INSERT INTO tb_device_category(category_name,description) VALUES
 ('测量仪器','电信号与物理量测量'),('计算设备','计算机及服务器'),('网络设备','网络教学设备'),('机械设备','机械加工教学'),('光学设备','光学实验');
INSERT INTO tb_device(device_no,device_name,model,category_id,lab_id,price,purchase_date) VALUES
 ('EQ001','数字示波器','DS1000',1,1,3500,'2024-09-01'),('EQ002','实验计算机','PC-01',2,3,5000,'2024-09-01'),
 ('EQ003','教学交换机','SW-24',3,3,2800,'2024-09-01'),('EQ004','小型车床','LATHE-1',4,4,12000,'2023-09-01'),
 ('EQ005','光学显微镜','MIC-01',5,5,6500,'2024-03-01');
INSERT INTO tb_booking(user_id,device_id,start_time,end_time,status,approver_id) VALUES
 (3,1,DATE_SUB(CURDATE(),INTERVAL 120 DAY)+INTERVAL 9 HOUR,DATE_SUB(CURDATE(),INTERVAL 120 DAY)+INTERVAL 11 HOUR,'已使用',1),
 (4,2,DATE_SUB(CURDATE(),INTERVAL 90 DAY)+INTERVAL 9 HOUR,DATE_SUB(CURDATE(),INTERVAL 90 DAY)+INTERVAL 11 HOUR,'已使用',1),
 (5,3,DATE_SUB(CURDATE(),INTERVAL 60 DAY)+INTERVAL 9 HOUR,DATE_SUB(CURDATE(),INTERVAL 60 DAY)+INTERVAL 11 HOUR,'已使用',2),
 (3,4,DATE_SUB(CURDATE(),INTERVAL 30 DAY)+INTERVAL 9 HOUR,DATE_SUB(CURDATE(),INTERVAL 30 DAY)+INTERVAL 11 HOUR,'已使用',2),
 (4,5,DATE_SUB(CURDATE(),INTERVAL 7 DAY)+INTERVAL 9 HOUR,DATE_SUB(CURDATE(),INTERVAL 7 DAY)+INTERVAL 11 HOUR,'已使用',1),
 (3,1,CURDATE()+INTERVAL 1 DAY+INTERVAL 9 HOUR,CURDATE()+INTERVAL 1 DAY+INTERVAL 11 HOUR,'待审核',NULL);
INSERT INTO tb_usage(booking_id,use_start,use_end)
 SELECT booking_id,start_time,end_time FROM tb_booking WHERE status='已使用';
INSERT INTO tb_maintenance(device_id,maintain_type,content,maintainer,cost,maintain_date,status,completed_at) VALUES
 (1,'保养','探头检查与清洁','实验员',20,DATE_SUB(CURDATE(),INTERVAL 110 DAY),'已完成',DATE_SUB(NOW(),INTERVAL 110 DAY)),
 (2,'维修','更换内存条','实验员',180,DATE_SUB(CURDATE(),INTERVAL 80 DAY),'已完成',DATE_SUB(NOW(),INTERVAL 79 DAY)),
 (3,'保养','端口清理','实验员',0,DATE_SUB(CURDATE(),INTERVAL 50 DAY),'已完成',DATE_SUB(NOW(),INTERVAL 50 DAY)),
 (4,'保养','导轨润滑','实验员',50,DATE_SUB(CURDATE(),INTERVAL 20 DAY),'已完成',DATE_SUB(NOW(),INTERVAL 20 DAY)),
 (5,'维修','光源检查','实验员',0,CURDATE(),'处理中',NULL);
INSERT INTO tb_log(user_id,action) VALUES (1,'初始化用户'),(1,'初始化实验室'),(1,'初始化设备'),(1,'初始化预约及使用记录'),(1,'初始化维保记录');
