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