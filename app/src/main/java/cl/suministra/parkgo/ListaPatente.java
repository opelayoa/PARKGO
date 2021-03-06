package cl.suministra.parkgo;

import android.app.ProgressDialog;
import android.database.Cursor;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ListaPatente extends AppCompatActivity {

    private Button BTN_Imprimir_Lista;
    private Button BTN_Actualizar_Lista;
    private ListView LST_Patente;
    private TextView TV_Patente;
    private TextView TV_Fecha_IN;
    private TextView TV_Usuario_IN;
    private TextView TV_Maquina_IN;
    private TextView TV_Espacios;
    private TextView TV_Minutos;
    private TextView TV_Precio;

    private ProgressDialog esperaDialog;

    CustomAdapter customAdapter = null;

    Print_Thread printThread = null;
    List<PatentesPendiente> patentesList = new ArrayList<PatentesPendiente>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_lista_patente);
        inicio();
    }

    private void inicio(){

        LST_Patente = (ListView) findViewById(R.id.LST_Patente);

        ConsultaPatentesPendiente();

        BTN_Imprimir_Lista = (Button) findViewById(R.id.BTN_Imprimir_Lista);
        BTN_Imprimir_Lista.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(patentesList.size() > 0) {

                    try{
                        /** IMPRIME LA ETIQUETA **/
                        if (printThread != null && !printThread.isThreadFinished()) {
                            Log.d(AppHelper.LOG_PRINT, "Thread is still running...");
                            return;
                        }

                        printThread = new Print_Thread(2,patentesList);
                        printThread.start();
                        printThread.join();
                        //printThread.getRESULT_CODE();

                    } catch (InterruptedException e) {
                        Util.alertDialog(ListaPatente.this,"InterruptedException Lista Patente", e.getMessage());
                    }

                }else{
                    Util.alertDialog(ListaPatente.this,"Lista Patentes", "No hay patentes para imprimir");
                }
            }
        });

        BTN_Actualizar_Lista = (Button) findViewById(R.id.BTN_Actualizar_Lista);
        BTN_Actualizar_Lista.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                esperaDialog = ProgressDialog.show(ListaPatente.this, "", "Actualizando...", true);
                esperaDialog.show();
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {

                        ConsultaPatentesPendiente();

                        esperaDialog.dismiss();
                    }
                }, 500);

            }
        });

    }

    private void ConsultaPatentesPendiente(){

        patentesList.clear();
        String[] args = new String[]{String.valueOf(AppHelper.getUbicacion_id()),"0"};
        Cursor c = AppHelper.getParkgoSQLite().rawQuery("SELECT\n" +
                                                        "trp.id, trp.patente, trp.espacios, trp.fecha_hora_in, trp.rut_usuario_in, trp.maquina_in, \n" +
                                                        "datetime('now','localtime') as fecha_hora_out,\n" +
                                                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(trp.fecha_hora_in)) As Integer) as dias,\n" +
                                                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(trp.fecha_hora_in)) * 24 As Integer) as horas,\n" +
                                                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(trp.fecha_hora_in)) * 24 * 60 As Integer) as minutos,\n" +
                                                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(trp.fecha_hora_in)) * 24 * 60 * 60 As Integer) as segundos,\n" +
                                                        "trp.finalizado, tcu.id_cliente \n" +
                                                        "FROM tb_registro_patentes trp\n" +
                                                        "INNER JOIN tb_cliente_ubicaciones tcu ON tcu.id = trp.id_cliente_ubicacion\n" +
                                                        "WHERE trp.id_cliente_ubicacion=? AND trp.finalizado =? \n"+
                                                        "ORDER BY trp.patente ASC", args);
        if (c.moveToFirst()) {
            do {


                String rs_id      = c.getString(0);
                String rs_patente = c.getString(1);
                int rs_espacios   = c.getInt(2);
                String rs_fecha_hora_in  = c.getString(3);
                String rs_rut_usuario_in = c.getString(4);
                String rs_maquina_in     = c.getString(5);
                int rs_minutos           = c.getInt(9);
                int rs_id_cliente = c.getInt(12);

                int precio      = 0;
                int total_minutos =  (rs_minutos - AppHelper.getMinutos_gratis());
                if (total_minutos > 0) {
                    precio = Util.calcularPrecio(total_minutos, rs_espacios, 0, 0);
                }

                int descuento_porciento = AppCRUD.getDescuentoGrupoConductor(ListaPatente.this, rs_patente);
                precio = Util.redondearPrecio(precio, descuento_porciento);

                PatentesPendiente patentePendiente = new PatentesPendiente(rs_patente,rs_fecha_hora_in, rs_rut_usuario_in , rs_maquina_in,
                                                                           rs_espacios, rs_minutos, precio);
                patentesList.add(patentePendiente);

            } while(c.moveToNext());

            customAdapter = new CustomAdapter();
            customAdapter.notifyDataSetChanged();
            LST_Patente.setAdapter(customAdapter);

        }

    }

    class CustomAdapter extends BaseAdapter{


        @Override
        public int getCount() {
            return patentesList.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = getLayoutInflater().inflate(R.layout.activity_lista_patente_custom, null);
            //IMG_Vehiculo = (ImageView) convertView.findViewById(R.id.IMG_Vehiculo);
            TV_Patente    = (TextView)  convertView.findViewById(R.id.TV_Patente);
            TV_Fecha_IN   = (TextView)  convertView.findViewById(R.id.TV_Fecha_IN);
            TV_Usuario_IN = (TextView)  convertView.findViewById(R.id.TV_Usuario_IN);
            TV_Maquina_IN = (TextView)  convertView.findViewById(R.id.TV_Maquina_IN);
            TV_Espacios   = (TextView)  convertView.findViewById(R.id.TV_Espacios);
            TV_Minutos    = (TextView)  convertView.findViewById(R.id.TV_Minutos);
            TV_Precio     = (TextView)  convertView.findViewById(R.id.TV_Precio);

            TV_Patente.setText(String.valueOf(patentesList.get(position).patente));
            TV_Fecha_IN.setText(String.valueOf(patentesList.get(position).fecha_in));
            TV_Usuario_IN.setText(String.valueOf(patentesList.get(position).usuario_in));
            TV_Maquina_IN.setText(String.valueOf(patentesList.get(position).maquina_in));
            TV_Espacios.setText(String.valueOf(patentesList.get(position).espacios));
            TV_Minutos.setText(String.format("%,d", patentesList.get(position).minutos).replace(",","."));
            TV_Precio.setText("$"+String.format("%,d", patentesList.get(position).precio).replace(",","."));

            return convertView;

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //Write your logic here
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    public class PatentesPendiente{

        String patente;
        String fecha_in;
        String usuario_in;
        String maquina_in;
        int espacios;
        int minutos;
        int precio;

        public PatentesPendiente(String patente, String fecha_in, String usuario_in, String maquina_in,
                                 int espacios, int minutos, int precio){
            this.patente  = patente;
            this.fecha_in = fecha_in;
            this.usuario_in = usuario_in;
            this.maquina_in = maquina_in;
            this.espacios = espacios;
            this.minutos  = minutos;
            this.precio   = precio;
        }

    }

}
