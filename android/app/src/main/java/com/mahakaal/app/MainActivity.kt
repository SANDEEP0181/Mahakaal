package com.mahakaal.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MahakaalApp()}}
}
@Composable fun MahakaalApp(){
 var user by remember{mutableStateOf("")}; var pass by remember{mutableStateOf("")}
 var logged by remember{mutableStateOf(false)}; var mining by remember{mutableStateOf(false)}
 var balance by remember{mutableStateOf("0")}; var message by remember{mutableStateOf("")}; var busy by remember{mutableStateOf(false)}
 suspend fun refresh(){val j=JSONObject(ApiClient.status());mining=j.optBoolean("mining");balance=j.optString("balance","0")}
 MaterialTheme{
  Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Text("Mahakaal",style=MaterialTheme.typography.headlineLarge);Text("KAAL Web3 Rewards",style=MaterialTheme.typography.titleMedium)
   if(!logged){
    OutlinedTextField(user,{user=it},label={Text("Username")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(pass,{pass=it},label={Text("Password")},modifier=Modifier.fillMaxWidth())
    Button(enabled=!busy,onClick={busy=true;message="Connecting…"},modifier=Modifier.fillMaxWidth()){Text("Login")}
    LaunchedEffect(busy){if(busy)try{val r=ApiClient.login(user,pass);ApiClient.token=JSONObject(r).getString("token");logged=true;refresh();message="Logged in"}catch(e:Exception){message="Login failed"}finally{busy=false}}
   }else{
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("KAAL Balance");Text(balance+" KAAL",style=MaterialTheme.typography.headlineMedium);Text(if(mining)"Mining active" else "Mining stopped")}}
    Button(enabled=!busy,onClick={busy=true;message="Working…"},modifier=Modifier.fillMaxWidth()){Text(if(mining)"Claim KAAL" else "Start Mining")}
    LaunchedEffect(busy){if(busy)try{if(mining){val r=ApiClient.claim();message="Claimed "+JSONObject(r).optString("claimed")+" KAAL"}else{ApiClient.start();message="Mining started"};refresh()}catch(e:Exception){message="Request failed"}finally{busy=false}}
    OutlinedButton(onClick={logged=false;ApiClient.token=null},modifier=Modifier.fillMaxWidth()){Text("Logout")}
   }
   if(message.isNotBlank())Text(message)
  }
 }
}