WK Proxy
=======
![Java 17.0+](https://img.shields.io/badge/Java-17.0%2B-blue.svg)
![GraalVM 22](https://img.shields.io/badge/GraalVM-22.0+-blue.svg)
[![GPL-3.0 license](https://img.shields.io/badge/license-GPL--3.0-green.svg)](https://www.gnu.org/licenses/gpl-3.0.html)

[English](https://github.com/catas-w/WK-Proxy/blob/master/README.md) | [中文](https://github.com/catas-w/WK-Proxy/blob/master/README_zh-CN.md)

WK Proxy 是一款开源的桌面端 HTTP/HTTPS 网络代理与抓包工具，支持 Windows 和 macOS 平台，致力于为开发者与测试人员提供简洁高效的网络调试体验

## 功能特点
- 基于 GraalVM 的 Java 原生编译，具备出色的性能表现与跨平台支持
- 支持 HTTP/HTTPS 代理与流量抓取，可拦截并解析请求与响应数据
- 自动生成根证书，一键安装，安全便捷地实现 HTTPS 解密
- 支持 WebSocket 代理，适配实时通信场景
- 请求限流与重发功能，便于模拟不同网络环境，提升测试覆盖与可靠性

## 即将支持
- 自定义请求的拦截与修改
- 使用 Python 脚本动态修改请求内容

## 预览截图
![image](screenshots/001.png)
![image](screenshots/002.png)
![image](screenshots/004.png)

## 安装
### 安装二进制包
1.	从 [Github Release](https://github.com/catas-w/WK-Proxy/releases/latest) 下载适配平台的可执行文件。
2.	按需配置运行环境（如必要的依赖项）。

### 从源码运行
- 依赖：JDK 17.0+, Maven 3.6.3+
```shell
git clone https://github.com/catas-w/WK-Proxy.git
cd WK-Proxy
mvn clean package
cd gui/target
java -jar gui-${version}.jar
```

## Contribution
欢迎贡献！有任何建议或意见您可以给我们提 [Issue](https://github.com/catas-w/WK-Proxy/issues), 
或联系本人 [catasw@foxmail.com](mailto:catasw@foxmail.com)

## Credits
本项目使用了以下优秀的开源项目，感谢他们的贡献：
- [GraalVM](https://www.graalvm.org)
- [GluonFX](https://gluonhq.com/products/gluonfx)
- [Netty](https://netty.io)
- [Proxyee](https://github.com/monkeyWie/proxyee)
- [JFoenix](http://www.jfoenix.com)
- [Ikonli](https://kordamp.org/ikonli/)
- ...
