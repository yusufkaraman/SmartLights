package bridge

import (
 "context"
 "encoding/json"
 "errors"
 "fmt"
 "net/http"
 "strings"
 "sync"
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

// One client per light keeps its session, so only the first request pays for
// the handshake. Requests to the same light run one at a time: Tapo bulbs drop
// sessions when two handshakes race.
var (
 mu sync.Mutex
 clients = map[string]tplink.SmartCaller{}
 locks = map[string]*sync.Mutex{}
 protos = map[string]string{}
)

func lockFor(ip string) *sync.Mutex {
 mu.Lock(); defer mu.Unlock()
 l, ok := locks[ip]; if !ok { l = &sync.Mutex{}; locks[ip] = l }
 return l
}

// Protocol names the protocol the last successful request to ip used.
func Protocol(ip string) string { mu.Lock(); defer mu.Unlock(); return protos[ip] }

// Forget drops the cached session for ip (or all when ip is empty).
func Forget(ip string) {
 mu.Lock(); defer mu.Unlock()
 if ip == "" { clients = map[string]tplink.SmartCaller{}; return }
 for k := range clients { if strings.HasPrefix(k, ip+"|") { delete(clients, k) } }
}

func Request(ip, email, password, method, params string) (string, error) {
 if ip == "" { return "", fmt.Errorf("IP adresi eksik") }
 var p map[string]any
 if params != "" { if err:=json.Unmarshal([]byte(params), &p); err!=nil{return "",err} }
 l := lockFor(ip); l.Lock(); defer l.Unlock()
 key := ip+"|"+email+"|"+password
 ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second); defer cancel()
 mu.Lock(); cached := clients[key]; mu.Unlock()
 if cached != nil {
  res, err := cached.Call(ctx, method, p)
  if err == nil { b,_:=json.Marshal(res); return string(b),nil }
  var se *tplink.SmartError
  if asSmart(err, &se) { return "", fmt.Errorf("[dev] %w", err) } // the device answered; the session is fine
  mu.Lock(); delete(clients, key); mu.Unlock()
 }
 hc := &http.Client{Timeout: 4*time.Second}
 type named struct{ name string; c tplink.SmartCaller }
 var callers []named
 if info, err := tplink.TPAPDiscover(ctx, hc, "http://"+ip); err == nil && info != nil {
  callers=append(callers,named{"TPAP",&tplink.TPAPClient{Base:info.BaseURL(ip),Password:password,HTTP:hc}})
 }
 callers=append(callers,named{"KLAP",&tplink.KLAPClient{Base:"http://"+ip,Auths:tplink.KLAPAuths(email,password),HTTP:hc}}, named{"AES",&tplink.AESClient{Base:"http://"+ip,Logins:tplink.AESLogins(email,password,2),HTTP:hc}})
 var errs []string
 kind := "net"
 for _, c := range callers {
  if ctx.Err() != nil { break }
  res, err := c.c.Call(ctx,method,p)
  if err == nil {
   mu.Lock(); clients[key]=c.c; protos[ip]=c.name; mu.Unlock()
   b,_:=json.Marshal(res); return string(b),nil
  }
  errs = append(errs, c.name+": "+err.Error())
  kind = worse(kind, classify(err))
 }
 if len(errs) == 0 { return "", fmt.Errorf("[net] zaman aşımı") }
 // The app reads the tag: [auth] wrong account, [locked] too many tries, [net] no answer, [dev] device error.
 return "", fmt.Errorf("[%s] %s", kind, strings.Join(errs, " | "))
}

func classify(err error) string {
 var se *tplink.SmartError
 switch {
 case errors.Is(err, tplink.ErrTPAPLocked): return "locked"
 case errors.Is(err, tplink.ErrKLAPAuth), errors.Is(err, tplink.ErrTPAPAuth): return "auth"
 case asSmart(err, &se): return "dev"
 }
 return "net"
}

func worse(a, b string) string {
 rank := map[string]int{"net":0, "dev":1, "auth":2, "locked":3}
 if rank[b] > rank[a] { return b }
 return a
}

func asSmart(err error, target **tplink.SmartError) bool {
 for e := err; e != nil; {
  if s, ok := e.(*tplink.SmartError); ok { *target = s; return true }
  u, ok := e.(interface{ Unwrap() error }); if !ok { return false }
  e = u.Unwrap()
 }
 return false
}
