package kr.co.ddophi.arminiproject;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.Collection;

public class MainActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private TextView txtTime, txtSlime, txtResult;
    private boolean firstSlime = true;
    private int leftSeconds = 59;
    private int leftSlime = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtTime = findViewById(R.id.txtTime);
        txtSlime = findViewById(R.id.txtSlime);
        txtResult = findViewById(R.id.txtResult);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdate);
    }

    //남은 시간 표시
    private void startTimer() {
        new Thread(()->{
            Log.d("테스트", "타이머 시작");
            while (leftSlime > 0 && leftSeconds >= 0){
                try {
                    Thread.sleep(1000);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }

                String ls;
                if(leftSeconds >= 10)
                    ls = "00:"+leftSeconds;
                else
                    ls = "00:0"+leftSeconds;

                runOnUiThread(() ->  txtTime.setText("남은 시간: " + ls));
                leftSeconds--;

                if(leftSeconds == -1){
                    runOnUiThread(() -> {
                        txtResult.setText("Fail..");
                        txtResult.setVisibility(View.VISIBLE);
                    });
                }
            }
        }).start();
    }

    //남은 슬라임수 표시
    private void showLeftSlime() {
            runOnUiThread(() ->  txtSlime.setText("남은 슬라임: " + leftSlime));
            if(leftSlime == 0){
                runOnUiThread(() -> {
                    txtResult.setText("Clear!!");
                    txtResult.setVisibility(View.VISIBLE);
                });
            }
    }

    //평면 감지 후 모델 자동 생성
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void onUpdate(FrameTime frameTime) {
        if(!firstSlime)
            return;

        Frame frame = arFragment.getArSceneView().getArFrame();
        Collection<Plane> planes = frame.getUpdatedTrackables(Plane.class);

        for(Plane plane : planes){
            if(plane.getTrackingState() == TrackingState.TRACKING){
                Anchor anchor = plane.createAnchor(plane.getCenterPose());
                makeModel(anchor);
                startTimer();
                showLeftSlime();
                firstSlime = false;
                break;
            }
        }
    }

    //모델 렌더링
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void makeModel(Anchor anchor){
        ModelRenderable.builder()
                .setSource(this, Uri.parse("slime.sfb"))
                .build()
                .thenAccept(modelRenderable -> addModel(anchor, modelRenderable))
                .exceptionally(throwable -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(throwable.getMessage()).show();
                    return null;
                });
    }

    //초기 모델 추가
    private void addModel(Anchor anchor, ModelRenderable modelRenderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);

        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());

        float cx = transformableNode.getLocalScale().x-0.21f;
        float cy = transformableNode.getLocalScale().y-0.21f;
        float cz = transformableNode.getLocalScale().z-0.21f;
        Vector3 nextScale = new Vector3(cx, cy, cz);

        transformableNode.setParent(anchorNode);
        transformableNode.setRenderable(modelRenderable);
        transformableNode.select();

        arFragment.getArSceneView().getScene().addChild(anchorNode);

        //생성한 모델 탭할 경우
        transformableNode.setOnTapListener((hitTestResult, motionEvent) -> {
            anchorNode.removeChild(transformableNode);
            arFragment.getArSceneView().getScene().onRemoveChild(anchorNode);
            splitModel(anchor, modelRenderable, nextScale);
        });
    }


    //모델 쪼개기
    private void splitModel(Anchor anchor, ModelRenderable modelRenderable, Vector3 scale){

        if(scale.x < 0) {
            leftSlime--;
            showLeftSlime();
            return;
        }
        else {
            leftSlime++;
            showLeftSlime();
        }

        float qx = anchor.getPose().qx();
        float qy = anchor.getPose().qy();
        float qz = anchor.getPose().qz();
        float qw = anchor.getPose().qw();

        float tx = anchor.getPose().tx();
        float ty = anchor.getPose().ty();
        float tz = anchor.getPose().tz();

        float rd_x = (float)Math.random()/2 ;
        float rd_y = (float)Math.random()/2 ;
        float rd_rotation;

        float[] pos1 = {tx+rd_x, ty+rd_y, tz};
        float[] pos2 = {tx-rd_x, ty-rd_y, tz};

        float[] rotation = {qx, qy, qz, qw};


        Session session = arFragment.getArSceneView().getSession();
        Anchor anchor1 =  session.createAnchor(new Pose(pos1, rotation));
        Anchor anchor2 =  session.createAnchor(new Pose(pos2, rotation));

        AnchorNode an1 = new AnchorNode(anchor1);
        AnchorNode an2 = new AnchorNode(anchor2);

        TransformableNode tn1 = new TransformableNode(arFragment.getTransformationSystem());
        TransformableNode tn2 = new TransformableNode(arFragment.getTransformationSystem());

        Vector3 nextScale = new Vector3((scale.x)-0.21f, (scale.y)-0.21f, (scale.z)-0.21f);

        tn1.getScaleController().setMinScale(0);
        tn2.getScaleController().setMinScale(0);

        tn1.setLocalScale(new Vector3(scale));
        tn2.setLocalScale(new Vector3(scale));

        rd_rotation = (float)Math.random()*360;
        Quaternion q1 = tn1.getLocalRotation();
        Quaternion q2 = Quaternion.axisAngle(new Vector3(0, 1f, 0f), rd_rotation);
        tn1.setLocalRotation(Quaternion.multiply(q1, q2));

        rd_rotation = (float)Math.random()*360;
        q1 = tn2.getLocalRotation();
        q2 = Quaternion.axisAngle(new Vector3(0, 1f, 0f), rd_rotation);
        tn2.setLocalRotation(Quaternion.multiply(q1, q2));

        tn1.setParent(an1);
        tn1.setRenderable(modelRenderable);
        tn1.select();

        tn2.setParent(an2);
        tn2.setRenderable(modelRenderable);
        tn2.select();

        arFragment.getArSceneView().getScene().addChild(an1);
        arFragment.getArSceneView().getScene().addChild(an2);

        tn1.setOnTapListener((hitTestResult, motionEvent) -> {
            an1.removeChild(tn1);
            arFragment.getArSceneView().getScene().onRemoveChild(an1);
            splitModel(anchor1, modelRenderable, nextScale);
        });

        tn2.setOnTapListener((hitTestResult, motionEvent) -> {
            an2.removeChild(tn2);
            arFragment.getArSceneView().getScene().onRemoveChild(an2);
            splitModel(anchor2, modelRenderable, nextScale);
        });
    }
}
