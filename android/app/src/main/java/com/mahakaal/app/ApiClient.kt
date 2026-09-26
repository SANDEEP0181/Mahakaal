package com.mahakaal.app
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
object ApiClient {
    var baseUrl="http://10.0.2.2:3000"
    var token:String?=null
    private suspend fun call(path:String,method:String="GET",body:String?=null):String=withContext(Dispatchers.IO){
        val c=URL(baseUrl+path).openConnection() as HttpURLConnection
        c.requestMethod=method; c.connectTimeout=8000; c.readTimeout=8000
        c.setRequestProperty("Content-Type","application/json")
        token?.let{c.setRequestProperty("Authorization","Bearer $it")}
        if(body!=null){c.doOutput=true;OutputStreamWriter(c.outputStream).use{it.write(body)}}
        val text=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().readText()
        if(c.responseCode !in 200..299) throw Exception(text)
        text
    }
    suspend fun login(username:String,password:String)=call("/api/v1/auth/login","POST","{"username":"$username","password":"$password"}")
    suspend fun register(username:String,password:String)=call("/api/v1/auth/register","POST","{"username":"$username","password":"$password"}")
    suspend fun status()=call("/api/v1/mining/status")
    suspend fun start()=call("/api/v1/mining/start","POST","{}")
    suspend fun claim()=call("/api/v1/mining/claim","POST","{}")
}