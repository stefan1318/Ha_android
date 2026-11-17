#!/usr/bin/env python3
"""Personalized offline diagnostic classifier, NOT microWakeWord or phone-ready."""
import argparse,csv,io,json,wave,zipfile
from pathlib import Path
import numpy as np
from scipy.signal import stft
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import confusion_matrix,balanced_accuracy_score
import joblib

def features(audio):
 a=audio.astype('float32')/32768
 _,_,s=stft(a,fs=16000,nperseg=512,noverlap=256,boundary=None)
 bands=np.array_split(np.abs(s)+1e-7,24,axis=0)
 return np.r_[[np.log1p(b.mean(axis=0)*100).mean() for b in bands],np.log1p(np.sqrt(np.mean(a*a))*100),len(a)/16000]

def main():
 p=argparse.ArgumentParser();p.add_argument('--archive',required=True);p.add_argument('--output',required=True);a=p.parse_args()
 out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.archive) as z:
  rows=list(csv.DictReader(io.TextIOWrapper(z.open('manifest.csv'))))
  X=[]
  for row in rows:
   with wave.open(io.BytesIO(z.read(row['relative_path']))) as w:
    assert (w.getframerate(),w.getnchannels(),w.getsampwidth())==(16000,1,2)
    audio=np.frombuffer(w.readframes(w.getnframes()),dtype='<i2')
   X.append(features(audio))
 X=np.asarray(X);y=np.array([int(r['label']=='positive') for r in rows])
 origin=np.array([r['origin'] for r in rows]);real=np.where(origin=='real_owner_approved')[0]
 pos=real[y[real]==1];neg=real[y[real]==0]
 rng=np.random.default_rng(20261117);rng.shuffle(pos);rng.shuffle(neg)
 real_train=np.r_[pos[12:],neg[4:]];real_test=np.r_[pos[:12],neg[:4]]
 synthetic=np.where(origin!='real_owner_approved')[0];rng.shuffle(synthetic)
 syn_pos=synthetic[y[synthetic]==1][:300];syn_neg=synthetic[y[synthetic]==0][:400]
 train=np.r_[real_train,syn_pos,syn_neg]
 model=make_pipeline(StandardScaler(),LogisticRegression(max_iter=1000,class_weight='balanced',random_state=42))
 weights=np.where(origin[train]=='real_owner_approved',15.,1.)
 model.fit(X[train],y[train],logisticregression__sample_weight=weights)
 pred=model.predict(X[real_test]);scores=model.predict_proba(X[real_test])[:,1]
 report={'type':'personalized_offline_spectral_baseline_NOT_microWakeWord','total_corpus':len(rows),'training_real_clips':len(real_train),'training_synthetic_clips':len(syn_pos)+len(syn_neg),'holdout_real_clips':len(real_test),'holdout_positive':12,'holdout_negative':4,'holdout_confusion_matrix_labels_negative_positive':confusion_matrix(y[real_test],pred,labels=[0,1]).tolist(),'holdout_balanced_accuracy':float(balanced_accuracy_score(y[real_test],pred)),'holdout_probabilities':np.round(scores,3).tolist(),'limitations':['Same speaker and recording sessions in train/test','Not streaming; no microWakeWord TFLite model','No false activations/hour or real-device measurements']}
 joblib.dump(model,out/'personalized_offline_baseline.joblib')
 (out/'report.json').write_text(json.dumps(report,indent=2))
 print(json.dumps(report,indent=2))
if __name__=='__main__':main()
