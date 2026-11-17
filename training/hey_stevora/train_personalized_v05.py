#!/usr/bin/env python3
"""Legacy non-streaming diagnostic baseline; not a personalized trainer.

Requires scikit-learn, numpy, scipy and joblib.
Reads a private approved v03 ZIP; never uploads recordings.
"""
import csv, zipfile, io, wave, json, os
import numpy as np
from scipy.signal import stft
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix, balanced_accuracy_score, precision_recall_fscore_support, roc_auc_score
import joblib
import argparse

def feat(a):
    a=a.astype(np.float32)/32768
    _,_,z=stft(a,fs=16000,nperseg=512,noverlap=256,boundary=None)
    p=np.abs(z)+1e-6
    bands=np.array_split(p,24,axis=0)
    v=np.array([np.log1p(b.mean(axis=0)*100).mean() for b in bands],dtype=np.float32)
    energy=np.sqrt(np.mean(a*a))
    return np.r_[v,np.log1p(energy*100),len(a)/16000].astype('float32')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--archive',required=True)
    parser.add_argument('--output',required=True)
    args=parser.parse_args()
    os.makedirs(args.output,exist_ok=True)
    with zipfile.ZipFile(args.archive) as z:
        rows=list(csv.DictReader(io.TextIOWrapper(z.open('manifest.csv'))))
        X=[];y=[];g=[];orig=[]
        for r in rows:
            with wave.open(io.BytesIO(z.read(r['relative_path']))) as w:
                assert (w.getframerate(),w.getnchannels(),w.getsampwidth())==(16000,1,2)
                a=np.frombuffer(w.readframes(w.getnframes()),dtype='<i2')
            X.append(feat(a));y.append(int(r['label']=='positive'))
            g.append(r['source_group']);orig.append(r['origin'])
    X=np.array(X);y=np.array(y);g=np.array(g);orig=np.array(orig)
    # synthetic_v01_unapproved is a legacy label; the owner later approved those clips.
    synth=np.where(np.isin(orig,['synthetic','synthetic_v01_unapproved']))[0]
    real=np.where(orig=='real_owner_approved')[0]
    train_s,test_s=next(GroupShuffleSplit(n_splits=1,test_size=.22,random_state=20261117).split(X[synth],y[synth],g[synth]))
    tr=synth[train_s];te=synth[test_s]
    model=make_pipeline(StandardScaler(),LogisticRegression(max_iter=700,class_weight='balanced',random_state=42))
    model.fit(X[tr],y[tr])
    pred=model.predict(X[te]);probs=model.predict_proba(X[te])[:,1]
    precision,recall,f1,_=precision_recall_fscore_support(y[te],pred,average='binary',zero_division=0)
    report={'status':'experimental_offline_classifier_not_microWakeWord',
            'dataset_clips':len(rows),'train_synthetic_clips':len(tr),
            'test_synthetic_clips':len(te),
            'test_source_groups':len(set(g[te])),
            'train_source_groups':len(set(g[tr])),
            'group_overlap':len(set(g[te])&set(g[tr])),
            'real_speaker_clips_excluded_from_training':len(real),
            'synthetic_holdout_confusion_matrix_negative_positive':confusion_matrix(y[te],pred,labels=[0,1]).tolist(),
            'synthetic_holdout_balanced_accuracy':float(balanced_accuracy_score(y[te],pred)),
            'synthetic_holdout_precision':float(precision),'synthetic_holdout_recall':float(recall),
            'synthetic_holdout_f1':float(f1),'synthetic_holdout_auc':float(roc_auc_score(y[te],probs)),
            'microWakeWord_tflite_exported':False,
            'limitations':'Non-streaming spectral classifier; synthetic speaker styles are not independent humans.'}
    if len(real):
        rp=model.predict(X[real])
        report['real_speaker_diagnostic']={'clips':len(real),
            'balanced_accuracy':float(balanced_accuracy_score(y[real],rp)),
            'confusion_matrix_negative_positive':confusion_matrix(y[real],rp,labels=[0,1]).tolist(),
            'note':'One held-out real speaker; not a multi-speaker validation.'}
    joblib.dump(model,os.path.join(args.output,'offline_baseline.joblib'))
    with open(os.path.join(args.output,'report.json'),'w') as f:json.dump(report,f,indent=2)
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
