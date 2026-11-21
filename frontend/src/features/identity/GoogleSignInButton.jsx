import { useEffect, useState } from 'react';

export default function GoogleSignInButton() {
  const [enabled,setEnabled]=useState(null);
  useEffect(()=>{
    let active=true;
    Promise.resolve().then(()=>fetch('/api/v1/auth/providers',{credentials:'same-origin',headers:{Accept:'application/json'}}))
      .then(response=>response.ok?response.json():Promise.reject(new Error('Unavailable')))
      .then(providers=>{if(active)setEnabled(providers.googleEnabled===true);})
      .catch(()=>{if(active)setEnabled(false);});
    return()=>{active=false;};
  },[]);
  return enabled?<a href="/oauth2/authorization/google" className="google-button" role="button"><span aria-hidden="true">G</span> Continue with Google</a>
    :<button type="button" className="google-button" disabled>{enabled===null?'Checking Google sign-in…':'Google sign-in is unavailable'}</button>;
}
