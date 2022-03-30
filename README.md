# RefreshMyPassword
帮助快速更新OpenWrt内的闪讯密码  
Fuck you 闪讯  
仅在浙江电信闪讯，OpenWrt R21.4.18 (2021-05-19) / LuCI Master (git-21.114.56541-974fb04)下测试  
## 下载或编译
下载：在右边的Release内下载（也可能我懒得弄Release  
或编译：clone本项目，在Android Studio内打开，自行编译  
## 使用
填写路由器管理后台的用户名，密码。  
登陆路由器后台，访问对应l2tp接口的修改配置页面，此时路径为http://路由器IP/cgi-bin/luci/admin/network/network/接口名称，填写接口名称  
在接口界面按F12打开开发人员工具，切换到Network（网络）选项卡，点击保存&应用按钮，找到第一个名为http://路由器IP/cgi-bin/luci/admin/network/network/接口名称 的POST请求，点击payload选项卡，复制Form Data里面的内容，填写至Payload模板  
内容类似如下  
token: 7960b9d37c723011da75b379c7ddxxxx  
cbi.submit: 1  
tab.network.l2tp: general  
cbid.network.l2tp._fwzone: wan  
...  
cbid.network.l2tp.dns: 114.114.114.114  
cbid.network.l2tp.mtu: 1452  
cbi.apply: 保存&应用  

wifi信息和l2tp密码选项会从手机短信内获取，请确保给予相应权限，包括发送短信，读取短信，通知类短信，定位等权限  
填写完毕后点击保存设置，之后只要打开APP点击设置密码就会自动修改路由器l2tp连接的密码  

## Todo
在密码过期时自动发送短信更新  
在连接wifi，断网的状态下自动尝试重连  
在连接wifi，密码过期且断网的状态下自动设置新密码  
