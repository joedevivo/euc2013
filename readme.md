***
Let's Write A Test!
@joedevivo
basho

***
Why are we here?

***
To write a feature for riak with TDD

***
JMX Monitoring For Riak

***
Why Riak JMX?

* It's pretty isolated (we can digest it in the time we have)
* We don't need to talk about complexities of riak (widens our audience)
* It's closed source! (We have to focus on the tests and not the implementation)

-why jmx? because it doesn't assume understanding of some more complex features of riak

***
History Lesson:

### riak_jmx predates riak_test!

***
What does it all mean?

It means that we can't go full big tdd on it!

***
So, first things first. We write a basic test, to test the existing features

Start with an existing feature: riak jmx

So first, let's validate the existing functionality!

Fortunately, we did this when we migrated to riak_test for the 1.2 release.

***
Uh Oh! There's no real JMX client in Erlang, so let's make a quick little tool in java

***
Dump.java - iterates through JMX Bean's fields, outputs JSON which Erlang *CAN* parse

***
TODO: INSERT Dump.java output here

***
Now we have that AND an existing test for riak_stats we can copy that and make a test for jmx

***
TODO: insert snippet of verify_stats.erl

***
boom! We've got a test of the JMX feature

***
time passes as time tends to

***
Somebody (me) wants to update riak_jmx with moar features!

***
Step 1: riak_jmx rearchitecture +mvn to the build

(Can you believe it was based on make files?)

***
Run the test!

***
Paste output
It worked!

***
Then I heard somewhere at excessive number of crashes of the JMX bean could actually bring down riak! let's test it out!

***
We're going to need to kill some processes here... so what do those processes look like?

***
add this to the test
```
?assert(fail),
```

***
Now run it!

```
➜ make clean && make
➜ ./riak_test -c ee -t jmx_verify

So, we find ourselves in a tricky situation here. 
You've run a single test, and it has failed.
Would you like to leave Riak running in order to debug?
[Y/n] Y
```

***

```
➜ ps -ax
96410 ??         0:00.57 /usr/bin/java -server -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.port=41111 
➜ kill 96410
➜ ps -ax 
97142 ??         0:00.43 /usr/bin/java -server -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.port=41111 
```

***
So, I wrote a cool function to kill the java process in order to invoke riak_jmx's restart logic

***
```
kill_jmx() ->
    {0, JMXProcs} = rt:cmd("ps -ax | grep com.sun.management.jmxremote"),
    Pids = filter_procs(string:tokens(JMXProcs, "\n")),
    %%Pids = [ hd(string:tokens(Line, "\s")) || Line <- JMXProcLines],
    [ rt:cmd("kill -9 " ++ Pid) || Pid <- Pids].

filter_procs([]) -> [];
filter_procs([Proc|T]) ->
    case string:str(Proc, "java -server") of
        0 -> filter_procs(T);
        _ -> [hd(string:tokens(Proc, "\s"))| filter_procs(T)]
    end.
```

***
Oh no! killing processes is too slow!

took 2-3 seconds a proc

***
So what do we do? We cheat. tell jmx to start on port 80 (which is restricted)

```
JMXPort = 80,
Config = [{riak_jmx, [{enabled, true}, {port, JMXPort}]}],
[Node|[]] = rt:deploy_nodes(1, Config),
timer:sleep(20000),
    case net_adm:ping(Node) of
        pang ->
            lager:error("riak_jmx crash able to crash riak node"),
            ?assertEqual("riak_jmx crash able to crash riak node", true);
        _ ->
            yay
    end,
```

That's a bingo! JMX is now killing the entre cluster

***
What do we want to have happen now?

JMX should have retry logic, in case there's a port conflict that you can fix independently of riak
That retry logic should be separate of riak's erlang supervision tree.

***
Where we landed is that is should retry 10 times, and if that fails, try again in 10 minutes.

***
So we implemented that. there's a riak_jmx gen_server that monitors the Port to the riak_jmx jar. and handles the exits and what not.

***
The test passes!

***
But then a thing occurred to me.

Just because jmx isn't crashing anymore, doesn't mean the retry logic is working

***
```
lager:info("JMX server monitor ~s exited with code ~p. Retry #~p.",
                          [State#state.pid, Rc, Retry]),
```

***
If we run this

```
%% Let's make sure the thing's restarting as planned
lager:info("calling riak_jmx:stop() to reset retry counters"),
rpc:call(Node, riak_jmx, stop, ["stopping for test purposes"]),

lager:info("loading lager backend on node"),
rt:load_modules_on_nodes([riak_test_lager_backend], [Node]),
ok = rpc:call(Node, gen_event, add_handler, [lager_event, riak_test_lager_backend, [info, false]]),
ok = rpc:call(Node, lager, set_loglevel, [riak_test_lager_backend, info]),

lager:info("Now we're capturing logs on the node, let's start jmx"),
lager:info("calling riak_jmx:start() to get these retries started"),
rpc:call(Node, riak_jmx, start, []),

timer:sleep(40000), %% wait 2000 millis per restart + fudge factor
Logs = rpc:call(Node, riak_test_lager_backend, get_logs, []),

lager:info("It can fail, it can fail 10 times"),
RetryCount = lists:foldl(
    fun(Log, Sum) -> case re:run(element(7,element(2,Log)), "JMX server monitor .* exited with code .*\. Retry #.*", []) of
            {match, _} -> 1 + Sum;
            _ -> Sum
        end
    end, 
    0, Logs),
?assertEqual({retry_count, RetryCount}, {retry_count, 10}),
```

***
Cool, right?

***

So, what's the next feature?

***

PARITY

***

`Riak.getNodeGetFsmTimeMax();` ?= node_put_fsm_time_100

***
That's lame! Let's fix the test so it looks for stats that are equal to stats in the `/stats` call

We're going to introduce parity... let's start by looking at the existing test's process_key/1 method

***
```
process_key(<<"CPUNProcs">>) -> <<"cpu_nprocs">>;
process_key(<<"MemAllocated">>) -> <<"mem_allocated">>;
process_key(<<"MemTotal">>) -> <<"mem_total">>;
process_key(<<"NodeGets">>) -> <<"node_gets">>;
process_key(<<"NodeGetsTotal">>) -> <<"node_gets_total">>;
process_key(<<"NodePuts">>) -> <<"node_puts">>;
process_key(<<"NodePutsTotal">>) -> <<"node_puts_total">>;
process_key(<<"VnodeGets">>) -> <<"vnode_gets">>;
process_key(<<"VnodeGetsTotal">>) -> <<"vnode_gets_total">>;
process_key(<<"VnodePuts">>) -> <<"vnode_puts">>;
process_key(<<"VnodePutsTotal">>) -> <<"vnode_puts_total">>;
process_key(<<"PbcActive">>) -> <<"pbc_active">>;
process_key(<<"PbcConnects">>) -> <<"pbc_connects">>;
process_key(<<"PbcConnectsTotal">>) -> <<"pbc_connects_total">>;
process_key(<<"NodeName">>) -> <<"nodename">>;
process_key(<<"RingCreationSize">>) -> <<"ring_creation_size">>;
process_key(<<"CpuAvg1">>) -> <<"cpu_avg1">>;
process_key(<<"CpuAvg5">>) -> <<"cpu_avg5">>;
process_key(<<"CpuAvg15">>) -> <<"cpu_avg15">>;
process_key(<<"NodeGetFsmTime95">>) -> <<"node_get_fsm_time_95">>;
process_key(<<"NodeGetFsmTime99">>) -> <<"node_get_fsm_time_99">>;
process_key(<<"NodeGetFsmTimeMax">>) -> <<"node_get_fsm_time_100">>;
process_key(<<"NodeGetFsmTimeMean">>) -> <<"node_get_fsm_time_mean">>;
process_key(<<"NodeGetFsmTimeMedian">>) -> <<"node_get_fsm_time_median">>;
process_key(<<"NodePutFsmTime95">>) -> <<"node_put_fsm_time_95">>;
process_key(<<"NodePutFsmTime99">>) -> <<"node_put_fsm_time_99">>;
process_key(<<"NodePutFsmTimeMax">>) -> <<"node_put_fsm_time_100">>;
process_key(<<"NodePutFsmTimeMean">>) -> <<"node_put_fsm_time_mean">>;
process_key(<<"NodePutFsmTimeMedian">>) -> <<"node_put_fsm_time_median">>;
process_key(<<"ReadRepairs">>) -> <<"read_repairs">>;
process_key(<<"ReadRepairsTotal">>) -> <<"read_repairs_total">>.
```

***

Run it!

```
10:55:20.731 [error] 
================ jmx_verify failure stack trace =====================
{function_clause,[{jmx_verify,process_key,
                              [<<"riak_kv_stat_ts">>],
                              [{file,"tests/jmx_verify.erl"},{line,148}]},
                  {jmx_verify,'-jmx_dump/1-lc$^0/1-0-',1,
                              [{file,"tests/jmx_verify.erl"},{line,146}]},
                  {jmx_verify,confirm,0,
                              [{file,"tests/jmx_verify.erl"},{line,42}]}]}
=====================================================================
```

***
Replace with this:  `process_key(Key) -> Key.`

because we don't want the test framework to explode on unexpected stats.
The current implementation has a finite set, but the new impl should be able to grow dynamically

***
But really, we'll want to remove process_key entirely because the whole point of process_key
is to compensate for the exisiting lack of parity.


***
Once we do that, the test fails in the current implementation
```
11:22:07.096 [error] 
================ jmx_verify failure stack trace =====================
{{assertNotEqual_failed,[{module,jmx_verify},
                         {line,129},
                         {expression,"{ Key , 0 }"},
                         {value,{<<"cpu_nprocs">>,0}}]},
 [{jmx_verify,'-verify_nz/2-fun-0-',2,
              [{file,"tests/jmx_verify.erl"},{line,129}]},
  {jmx_verify,'-verify_nz/2-lc$^0/1-0-',2,
              [{file,"tests/jmx_verify.erl"},{line,129}]},
  {jmx_verify,confirm,0,[{file,"tests/jmx_verify.erl"},{line,45}]}]}
=====================================================================
```

***
It's failing now that because the test is now looking for the field names from /stats, not the camel case java stuff

***
hand wavey jmx rewrite

***
it works!

***
Oh no! We over complicated things!

***
It still works