package bridge

import (
 "context"
 "encoding/json"
 "fmt"
 "net/http"
 "strings"
 "time"
 "github.com/cB-Sigma/tplink"
)

func Discover(target string) (string, error) {
 if target == "" { target = "255.255.255.255" }
 ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second); defer cancel()
 found, err := tplink.TapoDiscover(ctx, target+":20002", 3*time.Second)
 if err != nil { return "", err }
 out := []map[string]string{}
 for _, f := range found {
  if !strings.Contains(strings.ToUpper(f.DeviceType), "BULB") && !strings.Contains(strings.ToUpper(f.DeviceType), "LIGHT") { continue }
  out = append(out, map[string]string{"ip":f.Host,"id":f.DeviceID,"name":f.Name,"model":f.Model,"protocol":f.Protocol()})
 }
 b,_ := json.Marshal(out); return string(b),nil
}

func Request(ip, email, password, method, params string) (string, error) {
 if ip == "" { return "", fmt.Errorf("IP adresi eksik") }
 ctx, cancel := context.WithTimeout(context.Background(), 14*time.Second); defer cancel()
 hc := &http.Client{Timeout: 4*time.Second}
 var p map[string]any
 if params != "" { if err:=json.Unmarshal([]byte(params), &p); err!=nil{return "",err} }
 var callers []tplink.SmartCaller
 if info, err := tplink.TPAPDiscover(ctx, hc, "http://"+ip); err == nil && info != nil {
  callers=append(callers,&tplink.TPAPClient{Base:info.BaseURL(ip),Password:password,HTTP:hc})
 } else {
  callers=append(callers,&tplink.KLAPClient{Base:"http://"+ip,Auths:tplink.KLAPAuths(email,password),HTTP:hc}, &tplink.AESClient{Base:"http://"+ip,Logins:tplink.AESLogins(email,password,2),HTTP:hc})
 }
 var last error
 for _, c := range callers {
  res, err := c.Call(ctx,method,p); if err == nil { b,_:=json.Marshal(res); return string(b),nil }; last=err
 }
 return "",last
}
