package dev.localzet.rschs
import android.content.Context
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
data class AlertEvent(val time:Long,val type:String,val body:String)
object AlertStore {
 private const val PREF="events"; private const val KEY="items"
 fun classify(body:String):String? {
  val s=body.lowercase()
  if (!s.contains("рсчс") && !s.contains("опасност") && !s.contains("бпла") && !s.contains("ракет")) return null
  return when { s.contains("отбой")->"CLEAR"; s.contains("ракет")->"MISSILE"; s.contains("бпла")||s.contains("беспилот")->"UAV"; s.contains("опасност")||s.contains("тревог")->"ALERT"; else->null }
 }
 fun add(c:Context,sender:String,body:String,time:Long) {
  val type=classify(body)?:return; val a=raw(c)
  a.put(JSONObject().put("t",time).put("k",type).put("b",body).put("s",sender))
  c.getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply()
 }
 private fun raw(c:Context)=try{JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]"))}catch(_:Exception){JSONArray()}
 fun all(c:Context):List<AlertEvent>{
  val a=raw(c);val out=mutableListOf<AlertEvent>()
  for(i in 0 until a.length()){val o=a.getJSONObject(i);out+=AlertEvent(o.getLong("t"),o.getString("k"),o.getString("b"))}
  return out.distinctBy{it.time to it.body}.sortedBy{it.time}
 }
 fun importInbox(c:Context) {
  val cur=c.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf("date","body","address"),null,null,"date ASC")?:return
  cur.use { while(it.moveToNext()) add(c,it.getString(2)?:"",it.getString(1)?:"",it.getLong(0)) }
 }
}