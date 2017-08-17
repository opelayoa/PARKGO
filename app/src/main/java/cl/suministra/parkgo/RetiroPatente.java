package cl.suministra.parkgo;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.obm.mylibrary.PrintConnect;
import com.obm.mylibrary.PrintUnits;
import com.obm.mylibrary.ScanConnect;

public class RetiroPatente extends AppCompatActivity {

    private EditText EDT_Patente;
    private Button   BTN_RetiroPatente;
    private TextView TV_RS_Patente;
    private TextView TV_RS_Espacios;
    private TextView TV_RS_Fecha_IN;
    private TextView TV_RS_Fecha_OUT;
    private TextView TV_RS_Minutos;
    private TextView TV_RS_Precio;

    private ProgressDialog esperaDialog;

    public  PrintConnect mPrintConnect;
    private ScanConnect mScanConnect;
    private String data = "";
    private int    count= 0;

    //Variables utilizadas para finalizar la salida de la patente.
    private int g_id_registro_patente;
    private String g_patente;
    private int g_espacios;
    private String g_fecha_hora_in;
    private String g_fecha_hora_out;
    private int g_minutos;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case 11:
                    String str=msg.obj.toString();
                    if (data.equals(str)){
                        count++;
                    }else{
                        count=1;
                    }
                    data=str;
                    EDT_Patente.setText(str);
                    consultaRetiroPatente();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retiro_patente);
        mPrintConnect = new PrintConnect(this);
        mScanConnect  = new ScanConnect(this, mHandler);
        inicio();
    }

    public void inicio(){

        EDT_Patente = (EditText) findViewById(R.id.EDT_Patente);
        EDT_Patente.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.LB_Patente);
                    label.setText(EDT_Patente.getHint());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        EDT_Patente.setBackground(getDrawable(R.drawable.text_border_selector));
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                TextView label = (TextView) findViewById(R.id.MSJ_Patente);
                label.setText("");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.LB_Patente);
                    label.setText("");
                }
            }
        });


        EDT_Patente.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {

                if (event.getAction()!=KeyEvent.ACTION_UP)
                    return false;

                switch (event.getKeyCode()) {
                    case 66: //enter
                        consultaRetiroPatente();
                        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                        break;
                    default:
                        break;
                }

                return false;
            }
        });

        TV_RS_Patente   = (TextView) findViewById(R.id.TV_RS_Patente);
        TV_RS_Espacios  = (TextView) findViewById(R.id.TV_RS_Espacios);
        TV_RS_Fecha_IN  = (TextView) findViewById(R.id.TV_RS_Fecha_IN);
        TV_RS_Fecha_OUT = (TextView) findViewById(R.id.TV_RS_Fecha_OUT);
        TV_RS_Minutos   = (TextView) findViewById(R.id.TV_RS_Minutos);
        TV_RS_Precio    = (TextView) findViewById(R.id.TV_RS_Precio);

        BTN_RetiroPatente = (Button) findViewById(R.id.BTN_RetiroPatente);
        BTN_RetiroPatente.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v){
                if (g_id_registro_patente > 0 ) {
                    confirmDialog(RetiroPatente.this, "Confirme para retirar la patente " + g_patente);
                }else{
                    Toast.makeText(getApplicationContext(),"No ha ingresado patente a retirar, verifique ",Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    private void consultaRetiroPatente(){

        esperaDialog = ProgressDialog.show(this, "", "Consultando por favor espere...", true);
        esperaDialog.show();

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                procesoRetiroPatente();
                esperaDialog.dismiss();
            }
        }, 1500);

    }

    private void procesoRetiroPatente(){

        String patente = EDT_Patente.getText().toString();
        EDT_Patente.setText("");
        if (patente == null || patente.isEmpty()) {
            TextView view = (TextView) findViewById(R.id.MSJ_Patente);
            view.setText(EDT_Patente.getHint() + " no puede ser vacío");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                EDT_Patente.setBackground(getDrawable(R.drawable.text_border_error));
            }
        }else {

            try {

                String[] args = new String[]{patente, "0"};
                Cursor c = AppHelper.getParkgoSQLite().rawQuery("SELECT\n" +
                        "id, patente, fecha_hora_in,\n" +
                        "datetime('now','localtime') as fecha_hora_out,\n" +
                        "espacios,\n" +
                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(fecha_hora_in)) As Integer) as dias,\n" +
                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(fecha_hora_in)) * 24 As Integer) as horas,\n" +
                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(fecha_hora_in)) * 24 * 60 As Integer) as minutos,\n" +
                        "CAST((JulianDay(datetime('now','localtime')) - JulianDay(fecha_hora_in)) * 24 * 60 * 60 As Integer) as segundos\n" +
                        "FROM tb_registro_patente\n" +
                        "WHERE patente =? AND finalizado =?", args);
                if (c.moveToFirst()) {
                    int rs_id                = c.getInt(0);
                    String rs_patente        = c.getString(1);
                    String rs_fecha_hora_in  = c.getString(2);
                    String rs_fecha_hora_out = c.getString(3);
                    int rs_espacios = c.getInt(4);
                    int rs_dias     = c.getInt(5);
                    int rs_horas    = c.getInt(6);
                    int rs_minutos  = c.getInt(7);
                    int rs_segundos = c.getInt(8);
                    int precio      = 0;
                    int total_minutos =  (rs_minutos - AppHelper.minutos_gratis);
                    if (total_minutos > 0){
                        precio = total_minutos * AppHelper.valor_minuto;
                    }

                    TV_RS_Patente.setText("Patente:    " + rs_patente);
                    TV_RS_Espacios.setText("Espacios: " + rs_espacios);
                    TV_RS_Fecha_IN.setText("Fecha ingreso: " + rs_fecha_hora_in);
                    TV_RS_Fecha_OUT.setText("Fecha retiro:     " + rs_fecha_hora_out);
                    TV_RS_Minutos.setText("Tiempo:            " + String.format("%,d", rs_minutos).replace(",",".") + " min");
                    TV_RS_Precio.setText("Precio:              $" + String.format("%,d", precio).replace(",","."));

                    EDT_Patente.setText("");
                    //Setea las variables para finalizar el retiro.
                    g_id_registro_patente = rs_id;
                    g_patente        = rs_patente;
                    g_espacios       = rs_espacios;
                    g_fecha_hora_in  = rs_fecha_hora_in;
                    g_fecha_hora_out = rs_fecha_hora_out;
                    g_minutos        = rs_minutos;

                } else {
                    Toast.makeText(getApplicationContext(), "Patente: " + patente + " no registra ingreso, verifique", Toast.LENGTH_LONG).show();
                    reiniciaRetiro();
                }
                c.close();

            } catch (SQLException e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void confirmDialog(Context context, String mensaje) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder
                .setMessage(mensaje)
                .setPositiveButton("Si",  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String Resultado = actualizaPatenteRetiro(g_id_registro_patente, g_fecha_hora_out);
                        if (Resultado.equals("1")){
                            imprimeVoucherRetiro(g_patente, g_espacios, g_fecha_hora_in, g_fecha_hora_out, g_minutos);
                            Toast.makeText(getApplicationContext(),"Patente: "+g_patente+" retirada correctamente",Toast.LENGTH_LONG).show();
                        }else{
                            Toast.makeText(getApplicationContext(),Resultado,Toast.LENGTH_LONG).show();
                        }
                        reiniciaRetiro();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                })
                .show();
    }

    private String actualizaPatenteRetiro(int id_registro_patente, String fecha_hora_out){
        try{
            AppHelper.getParkgoSQLite().execSQL("UPDATE tb_registro_patente SET fecha_hora_out = '"+fecha_hora_out+"', minutos ="+g_minutos+", finalizado = '1' WHERE id = "+id_registro_patente);
        }catch(SQLException e){  return e.getMessage(); }

        return "1";
    }

    private void imprimeVoucherRetiro(String patente, int espacios, String fecha_hora_in,
                                      String fecha_hora_out, int minutos){

        int precio      = 0;
        int total_minutos =  (minutos - AppHelper.minutos_gratis);
        if (total_minutos > 0){
            precio = total_minutos * AppHelper.valor_minuto;
        }

        PrintUnits.setSpeed(mPrintConnect.os, 0);
        PrintUnits.setConcentration(mPrintConnect.os, 2);
        StringBuffer sb = new StringBuffer();
        sb.setLength(0);

        /** IMPRIME EL TEXTO **/
        String Texto    =   "--------------------------------"+"\n"+
                            "         TICKET SALIDA          "+"\n"+
                            "--------------------------------"+"\n"+
                            "Sistema de Transito Ordenado S.A"+"\n"+
                            "        RUT 96 852 690 1        "+"\n"+
                            "      Giro Estacionamiento      "+"\n"+
                            "         WWW.STOCHILE.CL        "+"\n"+
                            "--------------------------------"+"\n"+
                            "          San Antonio           "+"\n"+
                            "     Av Centenario 285 Of 2     "+"\n"+
                            " Consultas Reclamos 35-2212017  "+"\n"+
                            "      contacto@stochile.cl      "+"\n"+
                            "--------------------------------"+"\n"+
                            "Patente:  "+patente+"\n"+
                            "Espacios: "+espacios+"\n"+
                            "Ingreso:  "+fecha_hora_in+"\n"+
                            "Retiro:   "+fecha_hora_out+"\n"+
                            "Tiempo:   "+String.format("%,d", minutos).replace(",",".")+" min\n"+
                            "Precio:   $"+String.format("%,d", precio).replace(",",".");

        for (int i = 0; i < Texto.length(); i++) {
            sb.append(Texto.charAt(i));
        }
        sb.append("\n");
        mPrintConnect.send(sb.toString());

        /** IMPRIME ESPACIO PARA CORTAR ETIQUETA **/
        sb.setLength(0);
        for (int i = 0; i < 4; i++) {
            sb.append("\n");
        }
        mPrintConnect.send(sb.toString());
        EDT_Patente.setText("");
    }

    private void reiniciaRetiro(){

        EDT_Patente.setText("");
        TV_RS_Patente.setText("");
        TV_RS_Espacios.setText("");
        TV_RS_Fecha_IN.setText("");
        TV_RS_Fecha_OUT.setText("");
        TV_RS_Minutos.setText("");
        TV_RS_Precio.setText("");
        g_id_registro_patente = 0;
        g_patente             = "";
        g_espacios            = 0;
        g_fecha_hora_out      = "";
        g_fecha_hora_in       = "";
        g_minutos             = 0;

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        switch (event.getKeyCode()) {
            case 223:
                mScanConnect.scan();
                break;
            case 224:
                mScanConnect.scan();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mScanConnect.stop();
        mPrintConnect.stop();
    }


}