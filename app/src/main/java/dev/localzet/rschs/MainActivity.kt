package dev.localzet.rschs
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
class MainActivity:AppCompatActivity(){
 companion object{const val ACTION_REFRESH="dev.localzet.rschs.REFRESH"}
 private lateinit var root:LinearLayout
 private var day=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
 private val refresh=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){render()}}
 override fun onCreate(b:Bundle?){super.onCreate(b);root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,28,36,28);setBackgroundColor(Color.rgb(11,14,19))}
  setContentView(ScrollView(this).apply{addView(root)});registerReceiver(refresh,IntentFilter(ACTION_REFRESH),RECEIVER_NOT_EXPORTED)
  render(); if(ActivityCompat.checkSelfPermission(this,Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS),7) else {AlertStore.importInbox(this);render()}
 }
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)AlertStore.importInbox(this);render()}
 override fun onDestroy(){unregisterReceiver(refresh);super.onDestroy()}
 private fun tv(s:String,size:Float=16f,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(Color.WHITE);setPadding(0,8,0,8);if(bold)setTypeface(typeface,1)}
 private fun render(){root.removeAllViews();val all=AlertStore.all(this);val now=all.lastOrNull()?.type?:"CLEAR"
  val status=when(now){"CLEAR"->"ОТБОЙ";"UAV"->"ОПАСНОСТЬ БПЛА";"MISSILE"->"РАКЕТНАЯ ОПАСНОСТЬ";else->"ТРЕВОГА"}
  root.addView(tv("РСЧС  •  МОНИТОР",13f,true));root.addView(tv("Сейчас",15f));root.addView(tv(status,30f,true))
  root.addView(tv(if(now=="CLEAR") "По последнему сообщению активной тревоги нет" else "Активно по последнему SMS РСЧС",14f))
  val week=all.filter{it.time>=System.currentTimeMillis()-7*86400000L};val starts=week.filter{it.type!="CLEAR"}
  root.addView(tv("\nПоследние 7 дней",20f,true));root.addView(tv(starts.size.toString()+" сигналов  •  "+week.count{it.type=="CLEAR"}+" отбоев"))
  val hours=starts.map{val c=Calendar.getInstance();c.timeInMillis=it.time;c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE)}
  if(hours.isNotEmpty()){val avg=hours.average().roundToInt();root.addView(tv("Среднее время начала: "+String.format("%02d:%02d",avg/60,avg%60)))}
  root.addView(tv("Статистика прошлых дней не является прогнозом реальной угрозы.",12f))
  val nav=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val prev=Button(this).apply{text="‹";setOnClickListener{day-=86400000;render()}};val next=Button(this).apply{text="›";setOnClickListener{day+=86400000;render()}}
  val date=tv(SimpleDateFormat("dd MMMM, EEEE",Locale("ru")).format(Date(day)),18f,true);nav.addView(prev);nav.addView(date,LinearLayout.LayoutParams(0,-2,1f));nav.addView(next);root.addView(nav)
  root.addView(tv("Таймлайн",20f,true));val d=all.filter{it.time in day until day+86400000}
  if(d.isEmpty())root.addView(tv("Нет сообщений за этот день",14f))
  d.forEach{e->val t=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(e.time));val n=when(e.type){"CLEAR"->"✓ ОТБОЙ";"UAV"->"● БПЛА";"MISSILE"->"▲ РАКЕТНАЯ";else->"● ТРЕВОГА"};root.addView(tv(t+"   "+n,17f,true));root.addView(tv(e.body.replace("\n"," "),13f))}
 }
}