package cl.suministra.parkgo;

import android.database.Cursor;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;

/**
 * Created by LENOVO on 06-09-2017.
 */

public class AsyncSENDRetiroPatente extends AsyncTask<Void, Integer,  Boolean> {

    private AsyncHttpClient cliente = null;

    public void cancelTask(AsyncSENDRetiroPatente asyncSENDRetiroPatente) {
        if (asyncSENDRetiroPatente == null) return;
        asyncSENDRetiroPatente.cancel(false);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente onPreExecute");
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            int i = 1;
            do{
                publishProgress(i);
                i++;
                TimeUnit.SECONDS.sleep(1);
                isCancelled();
            }while(!isCancelled());
        } catch (InterruptedException e) {
            Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente InterruptedException "+e.getMessage());
        }
        return true;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        int progreso = values[0].intValue();
        autoRetiroPatentes();
        getPatentesRetiroPendienteSync();
        Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente onProgressUpdate "+progreso);
    }


    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if(result)
            Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente onPostExecute Finalizada");
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente onCancelled Cancelada");;
    }

    private void autoRetiroPatentes(){
        try{

            String[] args0 = new String[] {String.valueOf(AppHelper.getUbicacion_id()), "0"};
            Cursor c0 = AppHelper.getParkgoSQLite().rawQuery("SELECT trp.id, trp.patente, trp.espacios, trp.fecha_hora_in, tcu.id_cliente " +
                                                             "FROM tb_registro_patentes trp " +
                                                             "INNER JOIN tb_cliente_ubicaciones tcu ON tcu.id = trp.id_cliente_ubicacion " +
                                                             "WHERE trp.id_cliente_ubicacion=? AND trp.finalizado =? ", args0);
            if (c0.moveToFirst()){
                do{
                    String rs_id            = c0.getString(0);
                    String rs_patente       = c0.getString(1);
                    int    rs_espacios      = c0.getInt(2);
                    String rs_fecha_hora_in = c0.getString(3);
                    int    rs_id_cliente    = c0.getInt(4);

                    String nombre_dia_in    = Util.nombreDiaSemana(rs_fecha_hora_in);

                    String[] args1 = new String[] {String.valueOf(AppHelper.getUbicacion_id()),nombre_dia_in};
                    Cursor c1 = AppHelper.getParkgoSQLite().rawQuery("SELECT suma_dia, dia_hasta, hora_hasta FROM tb_cliente_ubicaciones_horarios "+
                                                                     "WHERE id_cliente_ubicacion =? AND dia_desde =? ", args1);
                    if (c1.moveToFirst()){
                        int suma_dia      = c1.getInt(0);
                        String dia_hasta  = c1.getString(1);
                        String hora_hasta = c1.getString(2);

                        Date fechahora_in          = AppHelper.fechaHoraFormat.parse(rs_fecha_hora_in);
                        Date fechahora_actual      = new Date();
                        Date fechahora_auto_retiro = AppHelper.fechaHoraFormat.parse(rs_fecha_hora_in.substring(0,10)+" "+hora_hasta);
                        fechahora_auto_retiro      = new Date(fechahora_auto_retiro.getTime() + TimeUnit.DAYS.toMillis(suma_dia));
                        String fechahora_out       = AppHelper.fechaHoraFormat.format(fechahora_auto_retiro);

                        //si la fechahora_acual es mayor a la fecha maxima fijada para el retiro de patente por ubicación.
                        if(fechahora_actual.after(fechahora_auto_retiro)){

                            long diff   = fechahora_auto_retiro.getTime() - fechahora_in.getTime();//as given
                            int minutos = (int) TimeUnit.MILLISECONDS.toMinutes(diff);

                            int precio = 0;
                            int total_minutos = (minutos - AppHelper.getMinutos_gratis());

                            //Calcula el precio, ya sea por minuto, tramo ó primer tramo mas minutos.
                            if (total_minutos > 0) {
                                precio = Util.calcularPrecio(total_minutos, rs_espacios, 0, 0);
                            }

                            //Aplica descuento de grupo conductor en caso que existe.
                            int descuento_porciento = AppCRUD.getDescuentoGrupoConductor(App.context, rs_patente);
                            precio = Util.redondearPrecio(precio, descuento_porciento);


                            try{

                                AppHelper.getParkgoSQLite().execSQL("UPDATE tb_registro_patentes " +
                                                                                "SET " +
                                                                                "fecha_hora_out = '"+fechahora_out+"', " +
                                                                                "rut_usuario_out = '0', " +
                                                                                "maquina_out = '"+AppHelper.getSerialNum()+"', " +
                                                                                "minutos = "+minutos+", " +
                                                                                "precio = "+precio+", " +
                                                                                "prepago = "+0+", " +
                                                                                "efectivo = "+0+", " +
                                                                                "finalizado = '1' " +
                                                                                "WHERE id = '"+rs_id+"'");
                                Log.d(AppHelper.LOG_TAG,"AsyncSENDRetiroPatente Registro ID "+rs_id+" finalizado automáticamente");
                            }catch(SQLException e){   Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente SQLException "+e.getMessage()); }

                        }
                    }
                    c1.close();

                } while(c0.moveToNext());
            }
            c0.close();

        }catch(SQLException e){
            Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente SQLException "+e.getMessage()); }
        catch (ParseException e) {
            Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente ParseException "+e.getMessage());
        }

    }

    private void getPatentesRetiroPendienteSync(){

        try{
            String[] args = new String[] {"1","0","1"};
            Cursor c = AppHelper.getParkgoSQLite().rawQuery("SELECT id, fecha_hora_out, rut_usuario_out, maquina_out, minutos, precio, prepago, efectivo " +
                                                            "FROM tb_registro_patentes WHERE enviado_in =? AND enviado_out =? AND finalizado =?", args);
            if (c.moveToFirst()){
                String rs_id = c.getString(0);
                String rs_fecha_hora_out = c.getString(1);
                String rs_rut_usuario_out= c.getString(2);
                String rs_maquina_out    = c.getString(3);
                int    rs_minutos        = c.getInt(4);
                int    rs_precio         = c.getInt(5);
                int    rs_prepago        = c.getInt(6);
                int    rs_efectivo       = c.getInt(7);
                sinncronizaRetiroPatente(rs_id, rs_fecha_hora_out, rs_rut_usuario_out, rs_maquina_out, rs_minutos, rs_precio, rs_prepago, rs_efectivo);
            }
            c.close();

        }catch(SQLException e){ Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente SQLException "+e.getMessage()); }

    }

    public void sinncronizaRetiroPatente(final String id_registro_patente, final String fecha_hora_out,
                                         final String rut_usuario_out, final String maquina_out, final int minutos,
                                         final int precio, final int prepago, final int efectivo) {

        cliente = new AsyncHttpClient();
        JSONObject jsonParams  = null;
        StringEntity entity    = null;
        try {
            jsonParams = new JSONObject();
            jsonParams.put("id",id_registro_patente);
            jsonParams.put("fecha_hora_out",fecha_hora_out);
            jsonParams.put("rut_usuario_out",rut_usuario_out);
            jsonParams.put("maquina_out",maquina_out);
            jsonParams.put("enviado_out",1);
            jsonParams.put("minutos",minutos);
            jsonParams.put("precio",precio);
            jsonParams.put("prepago",prepago);
            jsonParams.put("efectivo",efectivo);
            jsonParams.put("finalizado",1);
            entity = new StringEntity(jsonParams.toString());

            entity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()));
            cliente.post(App.context, AppHelper.getUrl_restful() + "registro_patentes/upt_out" , entity , ContentType.APPLICATION_JSON.getMimeType() , new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente onSuccess "+new String(responseBody));
                    try {

                        JSONObject jsonRootObject = new JSONObject(new String(responseBody));
                        JSONArray jsonArray       = jsonRootObject.optJSONArray("success");
                        if(jsonArray != null){
                            try{
                                //Marca el registro como enviado.
                                AppHelper.getParkgoSQLite().execSQL("UPDATE tb_registro_patentes SET enviado_out = '1' WHERE id = '"+id_registro_patente+"'");
                                Log.d(AppHelper.LOG_TAG,"AsyncSENDRetiroPatente REGISTRO ID "+id_registro_patente+" ENVIADO CORRECTAMENTE AL SERVIDOR");

                            }catch(SQLException e){  Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente SQLException "+e.getMessage());}
                        }else{
                            jsonArray = jsonRootObject.optJSONArray("error");
                            if(jsonArray != null){
                                JSONObject jsonObject = jsonArray.getJSONObject(0);
                                Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente ERROR RESPONSE "+jsonObject.optString("text"));
                            }
                        }
                    } catch (JSONException e) {
                        Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente JSONException "+e.getMessage());
                    }

                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente onFailure "+error.getMessage());
                    cliente.cancelRequests(App.context, true);
                    Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente onFailure cancelRequests");
                }

            });

        } catch (UnsupportedEncodingException e0) {
            Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente UnsupportedEncodingException "+e0.getMessage());
        } catch (JSONException e1) {
            Log.d(AppHelper.LOG_TAG, "AsyncSENDRetiroPatente UnsupportedEncodingException "+e1.getMessage());
        }

    }

}
