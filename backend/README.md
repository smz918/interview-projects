## 项目结构介绍
main.py:            项目入口
.env:               环境变量文件
### 项目名/scripts
deploy_nginx.sh:        一键部署nginx
uninstall_nginx.sh:     一键卸载nginx
deploy_fastapi.sh:      一键部署项目服务
uninstall_fastapi.sh:   一键卸载项目服务
### 项目名/logs
存放运行日志
### 项目名/database 
分别存放向量数据库（chromadb）和关系数据库（sqlite）
不过后期考虑将向量数据库替换Qdrant，增加Redis进行近期对话存储，不再用chromadb
关系数据库替换为PostgreSQL或者接入到Java那套系统的数据库
### 项目名/resources
现在主要存放用户的视频文件
音频和模板文件暂时使用了阿里的OSS，后续可以在这个目录同样存放，以便公共访问
### 项目名/test
一些功能的测试代码
### 项目名/H5
视频对话页面测试页面
### 项目名/app
#### 项目名/app/api
对外服务接口
#### 项目名/app/core
配置和日志功能
#### 项目名/app/database
数据库操作
#### 项目名/app/services
服务功能
#### 项目名/app/utils
工具方法