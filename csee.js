var siteBaseAddress = "/"; //站点根目录
var cookieDomain = document.domain.substr(document.domain.indexOf('.'));//cookie域
var CSEE = {
    //退出
    loginOut: function (callback) {
        this.delCookie("UserID")
        this.delCookie("ServerSide")
        this.delLocalCache("user");
        location.reload();
    },
    //显示登录弹层
    showLoginDialog: function () {
        var host = location.host.toLocaleLowerCase();
        var url = "//login.xueanquan.com/login?type=codeLogin";
        if (host.indexOf("test") > -1) {
            url = "//login-test.xueanquan.com/login?type=codeLogin";
        } else if (host.indexOf("pr") > -1) {
            url = "//login-pr.xueanquan.com/login?type=codeLogin";
        }       
        url += "&isForceLogin=noForce";
        var loginDialog = '\
          <div id="codeshow" style="position: fixed;z-index: 10000;top: 50%;right: 50%;margin-top: -80px;margin-right: -84px;width: 160px;height: 160px;display: none;">\
            <img id = "codeimgid" src = "" width = "100%" >\
          </div >\
          <div class="logintc" id="logintc" style="display:none;overflow:hidden;">\
          <iframe id="wx-login" scrolling="no" width="300" height="300" frameborder="0"  src="'+ url + '"></iframe>\
        </div>';
        $("body").append(loginDialog);
        $("#logintc #password").keydown(function (e) {
            if (e.keyCode == 13) {
                CSEE.login();
            }
        });
        show("logintc");
    },
    login: function (callback) {
        var loginname = $("#loginname").val();
        var password = $("#password").val();
        if ($.trim(loginname) == "") {
            $("#loginErrorInfo").html("请输入账号!");
            $("#loginErrorInfo").show();
            return false;
        }
        else if ($.trim(password) == "") {
            $("#loginErrorInfo").html("请输入密码!");
            $("#loginErrorInfo").show();
            return false;
        }
        $.ajax(
            {
                type: "POST",
                url: "https://appapi-dev" + cookieDomain + "/usercenter/api/v1/account/pc-uniteplatlogin",
                contentType: "application/json",
                data: JSON.stringify({
                    "username": loginname,
                    "password": password
                }),
                xhrFields: { withCredentials: true },
                success: function (res) {
                    if (res.result != null) {
                        if (callback)
                            callback(res);
                        else {
                            location.reload();
                        }
                    }
                    else {
                        $("#loginErrorInfo").html(res.message);
                        $("#loginErrorInfo").show();
                        return false;
                    }
                }
            });
    },
    //加载头部登录信息
    loadUserInfo: function (callback) {
        var serverSide = CSEE.getCookie("ServerSide") != null ? CSEE.getCookie("ServerSide") : null;
        if (serverSide != null)
            serverSide = serverSide.substr(0, serverSide.indexOf('.')) + cookieDomain;
        var userId = CSEE.getCookie("UserID");
        if (serverSide != null && serverSide != "-1" && serverSide != "" && userId != null) {
            var userJson = CSEE.getLocalCache("user");
            if (userJson != null && userJson.ukey == userId) {
                $("#loginLinkBtn").hide();
                $("#loginUserName").html(userJson.data.truename);
                $("#userSiteUrl").attr("href", decodeURIComponent(userJson.data.baseurl + "/mainpage.html"));
                $(".loginAfter").show();
                if (callback)
                    callback(userJson.data);
            }
            else {
                $.getJSON(serverSide + "/Education/Special.asmx/GetUserInfo?jsoncallback=?", { r: Math.random() }, function (json) {
                    if (json.userid >= 0) {
                        //设置本地缓存
                        CSEE.setLocalCache("user", { ukey: userId, data: json }, 3 * 60);

                        $("#loginLinkBtn").hide();
                        $("#loginUserName").html(json.truename);
                        $("#userSiteUrl").attr("href", decodeURIComponent(json.baseurl + "/mainpage.html"));
                        $(".loginAfter").show();
                    }
                    if (callback)
                        callback(json);
                });
            }
        }
    },
    //获取cookie
    getCookie: function (name) {
        var arr,
            reg = new RegExp("(^| )" + name + "=([^;]*)(;|$)");
        if (arr = document.cookie.match(reg))
            return unescape(arr[2]);
        else
            return null;
    },
    //设置cookie
    setCookie: function (c_name, value, expiredays) {
        var exdate = new Date();
        exdate.setDate(exdate.getDate() + expiredays);
        document.cookie = c_name + "=" + escape(value) + ((expiredays == null) ? "" : ";path=/;domain=" + cookieDomain + ";expires=" + exdate.toGMTString());
    },
    //删除cookie
    delCookie: function (name) {
        var exp = new Date();
        exp.setTime(exp.getTime() - 1);
        var cval = this.getCookie(name);
        if (cval != null) {
            document.cookie = name + "=" + cval + ";path=/;domain=" + cookieDomain + ";expires=" + exp.toGMTString();
        }
    },
    //设置本地缓存:键,值,有效分钟(本地所有缓存大小不能超过2M)
    setLocalCache: function (key, value, minute) {
        var ms = new Date().getTime() + minute * 60 * 1000;
        var cache = { data: value, expires: ms };
        try {
            store.set(key, cache);
        }
        catch (e) {
            if (e.name == "QuotaExceededError") {
                store.clearAll();
                store.set(key, cache);
            }
        }
    },
    //获取本地缓存
    getLocalCache: function (key) {
        var ms = new Date().getTime();
        var cache = store.get(key);
        if (cache != null) {
            if (ms < cache.expires) {
                return cache.data;
            }
            else {
                store.clear(key);
            }
        }
        return null;
    },
    //删除本地缓存
    delLocalCache: function (key) {
        store.clear(key);
    }
};

window.addEventListener('message', function (event) {
    if (event.data == 'ok' || event.data == 'accountLoginSuccess') {
        this.localStorage.removeItem("isWxLogin_" + CSEE.getCookie("UserID"));
        this.location.reload();
    } else if (event.data == 'closeLogin') {
        hide('logintc');
        //document.getElementById("logintc").src = '';
        //$('.login-dialog').hide();
        //$('#ioscodeimgid').hide();
        //$('#ioscodeshow').css({ "display": "none" });
        //$('#ioscodeimgid').attr('src', '');
        special.getServerSide();
    } else if (event.data == 'success' || event.data == 'codeLoginSuccess') {
        this.localStorage.removeItem("isWxLogin_" + CSEE.getCookie("UserID"));
        this.location.reload();
    }

    let data = event.data
    console.log('data', data)
    // 以下只在微信中时
    if (data && data.code && data.type !== 'noCode') {
        $('#codeimgid').attr('src', data.code)
    }
    if (data && data.type && data.type === 'loginCode') {
        // tab扫码时定位
        $('#codeshow').css({ "display": "block", "margin-top": "-80px" });
    } else if (data && data.type && data.type === 'forgetCode') {
        // 认证失败扫码时定位
        $('#codeshow').css({ "display": "block", "margin-top": "-46px" });
    } else if (data && data.type && data.type === 'bindCode') {
        // 帐密登录绑定扫码时定位
        $('#codeshow').css({ "display": "block", "margin-top": "-80px" });
    } else if (data && data.type && data.type === 'noCode') {
        // 需要关闭
        $('#codeshow').css({ "display": "none" });
        $('#codeimgid')[0].src = ''
    }
}, false);
