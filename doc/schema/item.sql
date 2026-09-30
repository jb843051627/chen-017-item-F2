-- item 政务服务事项标准化管理与实施清单同源发布管理 -- schema (chen-017)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_017

CREATE TABLE IF NOT EXISTS t_item_alert_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '办件逾期风险判定线代号（省里给的、局里加的，全局独占一个）',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  affair_type int DEFAULT NULL COMMENT '管的事由 1即办件 2承诺件 3转报件（各管各的）',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '低界逾期率(百分比，空着=不设这一段)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界逾期率(百分比，空着=不设这一段)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界逾期率(百分比，早年纸质录入可空，空着随中段回落)',
  eff_start datetime DEFAULT NULL COMMENT '上架那一天（新线自此刻起开口，含）',
  eff_end datetime DEFAULT NULL COMMENT '收档那一天/交棒之日(不含，旧线管到此刻为止)',
  priority int DEFAULT NULL COMMENT '尊卑次序(数值大的先开口；并列认代号排在后头的)',
  status int DEFAULT NULL COMMENT '旗 0在档 1收档(收档只许倒查来历，不进当下判定)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（换尺不删旧，历次留册）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  UNIQUE KEY uk_rule_code (rule_code),
  KEY idx_affair_eff (affair_type, status, eff_start, eff_end),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='办件逾期风险判定线（全市一册，一条尺一行）';

CREATE TABLE IF NOT EXISTS t_item_catalog_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '上级目录下发册次号',
  row_no int DEFAULT NULL COMMENT '原册行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被挑出的前置项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行待核要素项数',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已挂接 2挂起',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上级目录下发册核对行';

CREATE TABLE IF NOT EXISTS t_item_catalog_ver (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '事项目录版次单号',
  stage int DEFAULT NULL COMMENT '当前档次 0..3（草拟/复核/付发布/已上柜）',
  status int DEFAULT NULL COMMENT '版次落点 0未起 1在办 2已作废',
  content varchar(255) DEFAULT NULL COMMENT '版次记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项目录条目版次单';

CREATE TABLE IF NOT EXISTS t_item_change_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '要素调整核签单号',
  node_no int DEFAULT NULL COMMENT '当前所在道次 0..2（县核/市核/省定）',
  sign_mode int DEFAULT NULL COMMENT '同口并签办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本口应落名数',
  sign_count int DEFAULT NULL COMMENT '本口已落名数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项要素调整逐级核签单';

CREATE TABLE IF NOT EXISTS t_item_check_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '核查派单号',
  due_at datetime DEFAULT NULL COMMENT '该动手那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前几日开口派单',
  content varchar(255) DEFAULT NULL COMMENT '事由与被派部门记要',
  status int DEFAULT NULL COMMENT '条目情形 0未开口 1已派出 2办不成',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='要素定期核查派单';

CREATE TABLE IF NOT EXISTS t_item_dept_arch (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '承办部门底册代号',
  site_name varchar(128) DEFAULT NULL COMMENT '部门全称',
  site_type varchar(32) DEFAULT NULL COMMENT '机构类别',
  road_name varchar(128) DEFAULT NULL COMMENT '落在哪一级政务区划(省—市—县)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单卡年办件量低界(件/年)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界(件/年)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界(件/年)',
  status int DEFAULT NULL COMMENT '底册情形 0在编 1已撤并',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='承办部门底册';

CREATE TABLE IF NOT EXISTS t_item_implement_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '实施清单配置卡号',
  node_no int DEFAULT NULL COMMENT '本卡版次序',
  site_id int DEFAULT NULL COMMENT '所属承办部门底册',
  site_no varchar(64) DEFAULT NULL COMMENT '所属部门底册代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡申报年办件量(件/年)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本卡承诺时限(工作日)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版次序走）',
  grade_level int DEFAULT NULL COMMENT '年办件量折到的服务监督层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已冻住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实施清单配置卡';

-- 办件监察台账：只追加、不改口。评定成笔记一笔，销警另成一笔，两笔各留各的代号与日子。
CREATE TABLE IF NOT EXISTS t_item_supervise_record (
  id bigint NOT NULL COMMENT '主键',
  case_no varchar(64) DEFAULT NULL COMMENT '办件编号',
  affair_type int DEFAULT NULL COMMENT '事由 1即办件 2承诺件 3转报件',
  accept_at datetime DEFAULT NULL COMMENT '办件受理那一天（取尺凭它，不凭开屏今日）',
  overdue_rate decimal(12,2) DEFAULT NULL COMMENT '本回判定算到今日的逾期率(0..100)',
  record_kind int DEFAULT NULL COMMENT '本笔性质 1评定 2销警（回落销掉原警报） 9无从依凭',
  grade_level int DEFAULT NULL COMMENT '评出的等级 1低段 2中段 3高段 4超高段；无从依凭为空',
  rule_code varchar(64) DEFAULT NULL COMMENT '所依判定线代号（钉死，服务层给回）',
  rule_eff_start datetime DEFAULT NULL COMMENT '所依那条尺的上架之日（钉死，服务层给回）',
  alert_status int DEFAULT NULL COMMENT '警报情形 0在警 1已销（销警笔为1，新评定笔按等级起警）',
  link_record_id bigint DEFAULT NULL COMMENT '关联笔：销警笔指向被销的原评定笔',
  judged_at datetime DEFAULT NULL COMMENT '本回判定落账之时',
  del_flag int DEFAULT '0' COMMENT '删除标记（台账只追加，正常不用）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（无从依凭时写明：翻不出可用尺）',
  PRIMARY KEY (id),
  KEY idx_case_no (case_no),
  KEY idx_alert (alert_status, grade_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='办件监察台账（只追加，评定/销警各成一笔）';

-- 判定线种子：去年秋天市里换过一次尺，同一事由两条线并存（旧尺收档可倒查、新尺在档开口）；
-- 另有一条从纸质簿录入的老尺，高段空着——空高段不算断档，跟着中段往下落。
INSERT IGNORE INTO t_item_alert_rule
(id, rule_code, rule_name, affair_type, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES
-- 承诺件：年初纸质刻度（高段原本没填），秋天收档
(1001, 'P-PAPER-01', '承诺件逾期线（年初纸质三道，高段空）', 2, 5.00, 15.00, NULL,
 '2025-01-01 00:00:00', '2025-10-01 00:00:00', 100, 1, 0, 'seed', NOW(), '秋天换尺后收档，只许倒查来历'),
-- 承诺件：秋天立进系统的三道，在档
(1002, 'P-SYS-02', '承诺件逾期线（秋·系统三道）', 2, 5.00, 15.00, 30.00,
 '2025-10-01 00:00:00', NULL, 100, 0, 0, 'seed', NOW(), '自2025-10-01起开口，与旧尺同日半开交接'),
-- 即办件：省线与局线各管各的代号、同一事由并排在档，省线尊卑次序高
(2001, 'J-PROV-01', '即办件逾期线（省定）', 1, 2.00, 8.00, 20.00,
 '2025-01-01 00:00:00', NULL, 200, 0, 0, 'seed', NOW(), '省给代号，次序200'),
(2002, 'J-CITY-01', '即办件逾期线（局加）', 1, 3.00, 10.00, 25.00,
 '2025-01-01 00:00:00', NULL, 100, 0, 'seed', NOW(), '局里自加代号，次序100，同档事由听省线'),
-- 转报件：与省线次序并列的一条，用来验"次序分不出高下认代号排在后头的"（确定性，季季一样）
(3001, 'Z-PROV-01', '转报件逾期线（省定）', 3, 10.00, 30.00, 50.00,
 '2025-01-01 00:00:00', NULL, 100, 0, 0, 'seed', NOW(), '与 Z-SYS-09 次序并列，兜底认代号靠后者'),
(3002, 'Z-SYS-09', '转报件逾期线（局加·并列次序）', 3, 8.00, 25.00, 45.00,
 '2025-01-01 00:00:00', NULL, 100, 0, 0, 'seed', NOW(), '代号排序在 Z-PROV-01 之后，并列时它开口');

-- 初始档案数据（两条种子底册：一条在编、一条已撤并）
-- t_item_dept_arch 两条种子底册：id=0 在编、id=1 已撤并。
-- 单卡年办件量三线上限取 300/1500/6000 件。
INSERT IGNORE INTO t_item_dept_arch (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZW00', '青屏市承办部门底册（市政务服务和数据管理局，在编）', '行政许可', '青屏省—青屏市—青屏县', 300.00, 1500.00, 6000.00, 0, 0, 'seed', NOW()),
       (1, 'ZW01', '青屏市承办部门底册（市住房和城乡建设管理局，已撤并）', '行政确认', '青屏省—青屏市—青屏县', 300.00, 1500.00, 6000.00, 1, 0, 'seed', NOW());

-- 菜单与按钮权限（目录 9100：办件监察）
INSERT IGNORE INTO t_sys_permission
(id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time, update_by, update_time, remark)
VALUES
(9100, '办件监察', '办件逾期风险判定线与监察台账', '', 0, 0, '', 0, 'layui-icon layui-icon-vercode', 60, 0, 'seed', NOW(), NULL, NULL, NULL),
(9101, '判定线册', '判定线册展示', '/ItemAlertRuleController/view', 0, 9100, 'item:itemAlertRule:view', 1, 'layui-icon layui-icon-template', 1, 0, 'seed', NOW(), NULL, NULL, NULL),
(9102, '判定线集合', '判定线集合', '/ItemAlertRuleController/list', 0, 9101, 'item:itemAlertRule:list', 2, '', NULL, 0, 'seed', NOW(), NULL, NULL, NULL),
(9103, '判定线立新尺', '判定线立新尺', '/ItemAlertRuleController/add', 0, 9101, 'item:itemAlertRule:add', 2, 'layui-icon layui-icon-add-1', NULL, 0, 'seed', NOW(), NULL, NULL, NULL),
(9104, '判定线收档', '判定线收档', '/ItemAlertRuleController/archive', 0, 9101, 'item:itemAlertRule:edit', 2, 'layui-icon layui-icon-remove', NULL, 0, 'seed', NOW(), NULL, NULL, NULL),
(9110, '监察判定', '窗口自核与局监察共用判定屏', '/ItemSuperviseController/view', 0, 9100, 'item:itemSupervise:view', 1, 'layui-icon layui-icon-alarm', 2, 0, 'seed', NOW(), NULL, NULL, NULL),
(9111, '监察台账查询', '在警清单与单件台账', '/ItemSuperviseController/history', 0, 9110, 'item:itemSupervise:list', 2, '', NULL, 0, 'seed', NOW(), NULL, NULL, NULL),
(9112, '单办件判定落账', '单办件判定并钉台账', '/ItemSuperviseController/judge', 0, 9110, 'item:itemSupervise:judge', 2, 'layui-icon layui-icon-ok', NULL, 0, 'seed', NOW(), NULL, NULL, NULL);

