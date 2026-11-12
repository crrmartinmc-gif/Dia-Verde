/* La decodificación, reducción y codificación de la foto no bloquean el mapa. */
self.onmessage=async function(event){
  let image;
  try{
    image=await createImageBitmap(event.data,{resizeWidth:1000,resizeQuality:"medium"});
    const scale=Math.min(1,1000/Math.max(image.width,image.height));
    const canvas=new OffscreenCanvas(Math.max(1,Math.round(image.width*scale)),Math.max(1,Math.round(image.height*scale)));
    const context=canvas.getContext('2d');
    if(!context)throw new Error('Sin soporte de imagen');
    context.drawImage(image,0,0,canvas.width,canvas.height);
    const blob=await canvas.convertToBlob({type:'image/jpeg',quality:0.65});
    self.postMessage({data:new FileReaderSync().readAsDataURL(blob)});
  }catch(error){self.postMessage({error:String(error.message||error)});}
  finally{if(image)image.close();}
};

