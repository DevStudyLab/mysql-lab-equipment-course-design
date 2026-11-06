-- MySQL 5.7 / 8.x. Import ONLY into an empty database. No DROP statements.
CREATE TABLE tb_user (
 user_id INT PRIMARY KEY AUTO_INCREMENT,
 username VARCHAR(50) NOT NULL UNIQUE,
 password VARCHAR(255) NOT NULL,
 real_name VARCHAR(50) NOT NULL,
 role ENUM('ADMIN','STAFF','USER') NOT NULL,
 phone VARCHAR(20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE tb_lab (
 lab_id INT PRIMARY KEY AUTO_INCREMENT,
 lab_name VARCHAR(100) NOT NULL UNIQUE,
 location VARCHAR(100) NOT NULL,
 capacity INT NOT NULL,
 manager VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE tb_device_category (
 category_id INT PRIMARY KEY AUTO_INCREMENT,
 category_name VARCHAR(50) NOT NULL UNIQUE,
 description VARCHAR(200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE tb_device (
 device_id INT PRIMARY KEY AUTO_INCREMENT,
 device_no VARCHAR(50) NOT NULL UNIQUE,
 device_name VARCHAR(100) NOT NULL,
 model VARCHAR(50),
 category_id INT NOT NULL,
 lab_id INT NOT NULL,
 status ENUM('正常','维修中') NOT NULL DEFAULT '正常',
 price DECIMAL(10,2) NOT NULL DEFAULT 0,
 purchase_date DATE,
 FOREIGN KEY(category_id) REFERENCES tb_device_category(category_id),
 FOREIGN KEY(lab_id) REFERENCES tb_lab(lab_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE tb_booking (
 booking_id INT PRIMARY KEY AUTO_INCREMENT,
 user_id INT NOT NULL,
 device_id INT NOT NULL,
 start_time DATETIME NOT NULL,
 end_time DATETIME NOT NULL,
 status ENUM('待审核','通过','驳回','已使用','取消') NOT NULL DEFAULT '待审核',
 approver_id INT,
 FOREIGN KEY(user_id) REFERENCES tb_user(user_id),
 FOREIGN KEY(device_id) REFERENCES tb_device(device_id),
 FOREIGN KEY(approver_id) REFERENCES tb_user(user_id),
 INDEX idx_booking_device_time(device_id,status,start_time,end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE tb_usage (
 usage_id INT PRIMARY KEY AUTO_INCREMENT,
 booking_id INT NOT NULL UNIQUE,
 use_start DATETIME NOT NULL,
 use_end DATETIME,
 FOREIGN KEY(booking_id) REFERENCES tb_booking(booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE tb_maintenance (
 maintain_id INT PRIMARY KEY AUTO_INCREMENT,
 device_id INT NOT NULL,
 maintain_type ENUM('保养','维修') NOT NULL,
 content VARCHAR(200) NOT NULL,
 maintainer VARCHAR(50) NOT NULL,
 cost DECIMAL(10,2) NOT NULL DEFAULT 0,
 maintain_date DATE NOT NULL,
 status ENUM('处理中','已完成') NOT NULL DEFAULT '处理中',
 completed_at DATETIME,
 FOREIGN KEY(device_id) REFERENCES tb_device(device_id),
 INDEX idx_maintenance_device_status(device_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE tb_log (
 log_id INT PRIMARY KEY AUTO_INCREMENT,
 user_id INT,
 action VARCHAR(200) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES tb_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE VIEW v_booking_detail AS
 SELECT b.booking_id,b.user_id,u.real_name,b.device_id,d.device_no,d.device_name,l.lab_name,
 b.start_time,b.end_time,b.status,b.approver_id,a.real_name AS approver_name
 FROM tb_booking b JOIN tb_user u ON b.user_id=u.user_id
 JOIN tb_device d ON b.device_id=d.device_id JOIN tb_lab l ON d.lab_id=l.lab_id
 LEFT JOIN tb_user a ON b.approver_id=a.user_id;
CREATE VIEW v_device_status AS
 SELECT d.device_id,d.device_no,d.device_name,c.category_name,l.lab_name,d.status,
 CASE WHEN d.status='维修中' THEN '维修中'
 WHEN EXISTS(SELECT 1 FROM tb_booking b WHERE b.device_id=d.device_id
 AND b.status IN ('通过','已使用') AND b.start_time<=NOW() AND b.end_time>NOW()) THEN '预约占用'
 ELSE '空闲' END AS occupancy
 FROM tb_device d JOIN tb_device_category c ON d.category_id=c.category_id JOIN tb_lab l ON d.lab_id=l.lab_id;
CREATE VIEW v_maintenance_summary AS
 SELECT m.maintain_id,m.device_id,d.device_no,d.device_name,m.maintain_type,m.content,m.maintainer,
 m.cost,m.maintain_date,m.status,m.completed_at
 FROM tb_maintenance m JOIN tb_device d ON m.device_id=d.device_id;
CREATE VIEW v_usage_detail AS
 SELECT x.usage_id,x.booking_id,b.user_id,u.real_name,b.device_id,d.device_no,d.device_name,
 x.use_start,x.use_end,TIMESTAMPDIFF(MINUTE,x.use_start,x.use_end) AS duration_minutes
 FROM tb_usage x JOIN tb_booking b ON x.booking_id=b.booking_id
 JOIN tb_user u ON b.user_id=u.user_id JOIN tb_device d ON b.device_id=d.device_id;

DELIMITER $$
CREATE PROCEDURE sp_check_time_conflict(IN p_device INT, IN p_start DATETIME, IN p_end DATETIME, IN p_exclude INT, OUT p_count INT)
BEGIN
 SELECT COUNT(*) INTO p_count FROM tb_booking
 WHERE device_id=p_device AND booking_id<>p_exclude AND status IN ('待审核','通过','已使用')
 AND start_time<p_end AND end_time>p_start;
END$$
CREATE PROCEDURE sp_approve_booking(IN p_booking INT, IN p_approver INT)
BEGIN
 DECLARE v_device INT;
 DECLARE v_start DATETIME;
 DECLARE v_end DATETIME;
 DECLARE v_status VARCHAR(20);
 DECLARE v_count INT;
 DECLARE v_role VARCHAR(20);
 SELECT role INTO v_role FROM tb_user WHERE user_id=p_approver;
 IF v_role IS NULL OR v_role NOT IN ('ADMIN','STAFF') THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approval requires ADMIN or STAFF';
 END IF;
 SELECT device_id,start_time,end_time,status INTO v_device,v_start,v_end,v_status
 FROM tb_booking WHERE booking_id=p_booking FOR UPDATE;
 IF v_status IS NULL OR v_status<>'待审核' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Booking is not pending';
 END IF;
 IF (SELECT status FROM tb_device WHERE device_id=v_device)<>'正常' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Device is under maintenance';
 END IF;
 CALL sp_check_time_conflict(v_device,v_start,v_end,p_booking,v_count);
 IF v_count>0 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Booking time conflicts';
 END IF;
 UPDATE tb_booking SET status='通过',approver_id=p_approver WHERE booking_id=p_booking;
END$$
CREATE PROCEDURE sp_monthly_usage_stat(IN p_from DATETIME, IN p_to DATETIME)
BEGIN
 SELECT DATE_FORMAT(use_start,'%Y-%m') AS month,COUNT(*) AS usage_count
 FROM tb_usage WHERE use_start>=p_from AND use_start<p_to
 GROUP BY DATE_FORMAT(use_start,'%Y-%m') ORDER BY month;
END$$
CREATE TRIGGER trg_maintenance_insert AFTER INSERT ON tb_maintenance FOR EACH ROW
BEGIN
 IF NEW.status='处理中' THEN
   UPDATE tb_device SET status='维修中' WHERE device_id=NEW.device_id;
 END IF;
END$$
CREATE TRIGGER trg_maintenance_update AFTER UPDATE ON tb_maintenance FOR EACH ROW
BEGIN
 UPDATE tb_device SET status=IF(EXISTS(SELECT 1 FROM tb_maintenance
 WHERE device_id=NEW.device_id AND status='处理中'),'维修中','正常') WHERE device_id=NEW.device_id;
END$$
DELIMITER ;
