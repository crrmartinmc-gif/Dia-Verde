/* Screen Wake Lock evita el apagado automático; no habilita GPS en segundo plano. */
(function(root,factory){
  if(typeof module==='object' && module.exports)module.exports=factory();
  else root.DVScreenAwake=factory();
})(typeof globalThis==='object'?globalThis:this,function(){
  return class DVScreenAwake{
    constructor(options={}){
      this.navigator=options.navigator||globalThis.navigator;
      this.document=options.document||globalThis.document;
      this.onState=options.onState||(()=>{});
      this.schedule=options.schedule||setTimeout;
      this.cancel=options.cancel||clearTimeout;
      this.wanted=false;this.lock=null;this.pending=null;this.retry=null;this.generation=0;
    }
    emit(state){this.onState(state);}
    clearRetry(){if(this.retry!==null){this.cancel(this.retry);this.retry=null;}}
    retryLater(){
      this.clearRetry();
      if(!this.wanted || this.document.visibilityState!=='visible')return;
      this.retry=this.schedule(()=>{this.retry=null;this.acquire();},30000);
    }
    start(){this.wanted=true;return this.acquire();}
    acquire(){
      if(!this.wanted)return Promise.resolve();
      if(this.document.visibilityState!=='visible'){this.emit('hidden');return Promise.resolve();}
      if(!this.navigator?.wakeLock?.request){this.emit('unsupported');return Promise.resolve();}
      if(this.lock && !this.lock.released){this.emit('active');return Promise.resolve();}
      if(this.pending)return this.pending;
      this.clearRetry();this.emit('requesting');
      const generation=this.generation;
      const pending=Promise.resolve().then(()=>this.navigator.wakeLock.request('screen')).then(async lock=>{
        if(!this.wanted || generation!==this.generation || this.document.visibilityState!=='visible'){
          await lock.release();return;
        }
        this.lock=lock;
        lock.addEventListener('release',()=>{
          if(this.lock!==lock)return;
          this.lock=null;
          this.emit(this.wanted?'released':'idle');
          this.retryLater();
        });
        this.emit('active');
      }).catch(()=>{
        if(this.wanted && generation===this.generation){this.emit('denied');this.retryLater();}
      }).finally(()=>{if(this.pending===pending)this.pending=null;});
      this.pending=pending;return pending;
    }
    visibilityChanged(){
      if(!this.wanted)return;
      if(this.document.visibilityState==='visible')return this.acquire();
      this.clearRetry();
      const lock=this.lock;this.lock=null;
      if(lock)lock.release().catch(()=>{});
      this.emit('hidden');
    }
    async stop(){
      this.wanted=false;this.generation++;this.pending=null;this.clearRetry();
      const lock=this.lock;this.lock=null;this.emit('idle');
      if(lock){try{await lock.release();}catch(e){}}
    }
  };
});
