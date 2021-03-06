package com.coldthinder11.refreshmypassword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


@JsonClass(generateAdapter = true)
data class SaveDataJson(
    val user_name: String,
    val password: String,
    val l2tp_interface: String,
    val l2tp_password: String,
    val post_template: String
)

class MainActivity : AppCompatActivity() {
    var routerIp :String? = null;
    var is_exp = true;
    var wifi_ready = false;
    var exp_time :String? = null;

    fun getIpAddr(ip:Int): InetAddress?{
        val byteBuffer: ByteBuffer = ByteBuffer.allocate(4)
        var ipAddr: InetAddress?;
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.putInt(ip)
        try {
            ipAddr = InetAddress.getByAddress(null, byteBuffer.array())
            return ipAddr
        } catch (e: UnknownHostException) {
            return null;
        }
    }

    fun ReadPasswordSMS(){
        val cursor: Cursor? =
            contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, null)
        if(cursor == null) return
        if (cursor.moveToFirst()) {
            do {
                var msgData = ""
                var is_target_sms = false
                for (idx in 0 until cursor.columnCount) {
                    if(cursor.getColumnName(idx).toString() == "address" && cursor.getString(idx) == "106593005") is_target_sms = true
                    if(is_target_sms && cursor.getColumnName(idx).toString() == "body"){
                        val msg_body = cursor.getString(idx)
                        val target_str = "??????????????????"
                        if (msg_body.indexOf(target_str) > -1){
                            //????????????
                            val start_index = msg_body.indexOf(target_str) + target_str.length
                            val password = msg_body.substring(start_index,start_index + 6)
                            findViewById<EditText>(R.id.l2tpPasswordInputText).text.clear()
                            findViewById<EditText>(R.id.l2tpPasswordInputText).text.append(password)
                            //??????????????????
                            val time_target_str= "?????????"
                            val time_target_end_str= "????????????"
                            val expired_time = msg_body.substring(msg_body.indexOf(time_target_str) + time_target_str.length, msg_body.indexOf(time_target_end_str))
                            findViewById<TextView>(R.id.smsStatusText).text = ("??????:${password}\n????????????:${expired_time}")
                            val date = SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(expired_time);
                            if(date < Date()){
                                is_exp = true;
                                Toast.makeText(this,"???????????????",Toast.LENGTH_SHORT).show();
                            }
                            else {
                                is_exp = false;
                                Toast.makeText(this,"???????????????",Toast.LENGTH_SHORT).show();
                            }
                            return
                        }
                    }
                }
            } while (cursor.moveToNext())
        } else {
            // empty box, no SMS
        }
    }

    fun ReadConfig(){
        val config = File(getApplicationContext().getExternalFilesDir("")?.absolutePath + "data.json")
        if(!config.exists()) return;
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val jsonAdapter: JsonAdapter<SaveDataJson> =
            moshi.adapter<SaveDataJson>(SaveDataJson::class.java)
        val data = jsonAdapter.fromJson(config.readText())
        findViewById<EditText>(R.id.userNameTextInput).text.clear()
        findViewById<EditText>(R.id.userNameTextInput).text.append(data?.user_name)
        findViewById<EditText>(R.id.passwordInputText).text.append(data?.password)
        findViewById<EditText>(R.id.interfaceInputText).text.append(data?.l2tp_interface)
        findViewById<EditText>(R.id.l2tpPasswordInputText).text.append(data?.l2tp_password)
        findViewById<EditText>(R.id.payloadInputText).text.append(data?.post_template)
    }

    fun saveInfo(){
        val data = SaveDataJson(findViewById<EditText>(R.id.userNameTextInput).text.toString(),
            findViewById<EditText>(R.id.passwordInputText).text.toString(),
            findViewById<EditText>(R.id.interfaceInputText).text.toString(),
            findViewById<EditText>(R.id.l2tpPasswordInputText).text.toString(),
            findViewById<EditText>(R.id.payloadInputText).text.toString());
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val jsonAdapter: JsonAdapter<SaveDataJson> =
            moshi.adapter<SaveDataJson>(SaveDataJson::class.java)
        val json = jsonAdapter.toJson(data)
        try {
            val out = BufferedWriter(FileWriter(getApplicationContext().getExternalFilesDir("")?.absolutePath + "data.json"))
            out.write(json)
            out.close()
            Toast.makeText(this,"????????????",Toast.LENGTH_SHORT).show();
        } catch (e: IOException) {
            Toast.makeText(this,"??????????????????",Toast.LENGTH_SHORT).show();
        }
    }

    fun readWifiStatus(){
        val wifiStatus = findViewById<TextView>(R.id.wifiStatus);
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager;
        if (!wifiManager.isWifiEnabled()) {
            wifiStatus.text = "Wifi?????????????????????Wifi??????"
            return;
        }
        val info = wifiManager.connectionInfo;
        val ssid = info.ssid;
        var ipAddr: InetAddress? = getIpAddr(info.ipAddress);
        val dhcpInfo = wifiManager.dhcpInfo;
        val gateway = getIpAddr(dhcpInfo.gateway);
        routerIp = gateway.toString().substring(1);
        var luciStatus = "";
        try{
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder()
                .url("http://${routerIp}/cgi-bin/luci/")
                .get()
                .build()
            val resp = client.newCall(request).execute()
            val res = resp.body?.string()
            if(res != null){
                if(res.indexOf("LuCI") >= 0) {
                    wifi_ready = true;
                    luciStatus = "???????????????LuCI";
                }
                else luciStatus = "?????????????????????LuCI???????????????????????????"
            }
            else luciStatus = "?????????????????????LuCI???????????????????????????"

        }
        catch (e:Exception){
            luciStatus = "????????????luci?????????????????????????????????????????????"
        }
        wifiStatus.text = "SSID:${ssid}\nGateway:${routerIp}\n${luciStatus}";
    }

    fun setPassword2Router(){
        val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
        val cookieStore: HashMap<String, List<Cookie>> = HashMap()
        val client = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(httpUrl: HttpUrl, list: List<Cookie>) {
                    cookieStore[httpUrl.host] = list
                }
                override fun loadForRequest(httpUrl: HttpUrl): List<Cookie> {
                    val cookies = cookieStore[httpUrl.host]
                    return cookies ?: ArrayList()
                }
            })
            .build()
        //Login
        val body = FormBody.Builder().add("luci_username",findViewById<EditText>(R.id.userNameTextInput).text.toString())
            .add("luci_password",findViewById<EditText>(R.id.passwordInputText).text.toString()).build()
        var request = Request.Builder()
            .url("http://${routerIp}/cgi-bin/luci/")
            .post(body)
            .build()
        try {
            val resp = client.newCall(request).execute()
            if (resp.code == 320 || resp.code == 200){
                //Toast.makeText(this,"???????????????HTTP ${resp.code}",Toast.LENGTH_SHORT).show();
            }
            else{
                Toast.makeText(this,"???????????????HTTP ${resp.code}",Toast.LENGTH_SHORT).show();
                return;
            }
        }
        catch (e:java.lang.Exception){
            Toast.makeText(this,"????????????",Toast.LENGTH_SHORT).show();
            return;
        }
        request = Request.Builder()
            .url("http://${routerIp}/cgi-bin/luci/admin/network/network/${findViewById<EditText>(R.id.interfaceInputText).text.toString()}")
            .get()
            .build()
        try {
            val resp = client.newCall(request).execute()
            if (resp.code == 200){
                val content = resp.body?.string();
                val target_str = "name=\"token\" value=\"";
                if(content != null){
                    val token_index = content.indexOf(target_str)
                    if(token_index >=0){
                        val end_index = content.indexOf("\"",token_index+ target_str.length)
                        val token_str = content.substring(token_index+ target_str.length,end_index)
                        var body_builder = FormBody.Builder();
                        val post_template = findViewById<EditText>(R.id.payloadInputText).text.toString().trim()
                        val post_lines = post_template.lines()
                        for (line in post_lines){
                            val key = line.split(":")[0].trim()
                            var value = line.split(":")[1].trim()
                            when(key){
                                "token" -> value = token_str
                                "cbid.network.l2tp.password" -> value = findViewById<EditText>(R.id.l2tpPasswordInputText).text.toString()
                            }
                            body_builder = body_builder.add(key,value)
                        }
                        val req_body = body_builder.build()
                        val request = Request.Builder()
                            .url("http://${routerIp}/cgi-bin/luci/admin/network/network/${findViewById<EditText>(R.id.interfaceInputText).text}")
                            .post(req_body)
                            .build()
                        try {
                            val resp = client.newCall(request).execute()
                            if (resp.code == 200){
                                //?????????????????????????????????
                                val body = FormBody.Builder().add("token",token_str).build()
                                val request = Request.Builder()
                                    .url("http://${routerIp}/cgi-bin/luci/servicectl/restart/network,wireless,firewall")
                                    .post(body)
                                    .build()
                                val resp = client.newCall(request).execute()
                                Toast.makeText(this,"??????????????????",Toast.LENGTH_SHORT).show();
                            }
                            else{
                                Toast.makeText(this,"???????????????HTTP ${resp.code}",Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        catch (e:java.lang.Exception){
                            Toast.makeText(this,"????????????",Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    else throw java.lang.Exception()
                }
                else throw java.lang.Exception()
            }
            else{
                Toast.makeText(this,"???????????????HTTP ${resp.code}",Toast.LENGTH_SHORT).show();
                return;
            }
        }
        catch (e:java.lang.Exception){
            Toast.makeText(this,"????????????",Toast.LENGTH_SHORT).show();
            return;
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //??????????????????????????????????????????????????????????????????????????????UI
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //????????????????????????
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //???????????????????????????????????????????????????
                ActivityCompat.requestPermissions(this, Array<String>(1,{Manifest.permission.ACCESS_COARSE_LOCATION}), 1);
            }
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                //???????????????????????????????????????????????????
                ActivityCompat.requestPermissions(this, Array<String>(1,{Manifest.permission.READ_SMS}), 2);
            }
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                //???????????????????????????????????????????????????
                ActivityCompat.requestPermissions(this, Array<String>(1,{Manifest.permission.SEND_SMS}), 3);
            }
        }

        ReadConfig();
        readWifiStatus();
        ReadPasswordSMS();
        if(!is_exp && wifi_ready){
            setPassword2Router();
        }

        findViewById<Button>(R.id.saveDataButton).setOnClickListener {
            saveInfo();
        };
        findViewById<Button>(R.id.setPasswordButton).setOnClickListener {
            setPassword2Router();
        };
        findViewById<Button>(R.id.rereadButton).setOnClickListener{
            readWifiStatus();
        }
    }
}