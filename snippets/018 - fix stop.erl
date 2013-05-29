stop(Reason) ->
    error_logger:info_msg(io_lib:format("~p~n",[Reason])),
    init:stop().  


stop(Reason) ->
    error_logger:info_msg(io_lib:format("~p~n",[Reason])),
    application:stop(riak_jmx).  
