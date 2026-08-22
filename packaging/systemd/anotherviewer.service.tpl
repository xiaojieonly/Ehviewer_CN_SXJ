[Unit]
Description=AnotherViewer Web
After=network.target

[Service]
Type=simple
User=anotherviewer
WorkingDirectory=/opt/anotherviewer
# data-dir 经环境变量注入（与 deploy unit、install.sh 一致）。
# 注意：裸 --data-dir 不是 Spring 配置键——真实键为 anotherviewer.data-dir，
# 命令行须写 --anotherviewer.data-dir=...，故此处用环境变量形式。
Environment=ANOTHERVIEWER_DATA_DIR=/var/lib/anotherviewer
ExecStart=/usr/bin/java -jar /opt/anotherviewer/lib/app.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
