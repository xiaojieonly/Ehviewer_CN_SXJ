[Unit]
Description=AnotherViewer Web
After=network.target

[Service]
Type=simple
User=anotherviewer
WorkingDirectory=/opt/anotherviewer
ExecStart=/usr/bin/java -jar /opt/anotherviewer/lib/app.jar --data-dir=/var/lib/anotherviewer
Restart=on-failure

[Install]
WantedBy=multi-user.target
