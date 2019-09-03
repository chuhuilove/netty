/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;


/**
 * A list of {@link ChannelHandler}s which handles or intercepts inbound events and outbound operations of a
 * {@link Channel}.{@link ChannelPipeline} implements an advanced form of the
 * <a href="http://www.oracle.com/technetwork/java/interceptingfilter-142169.html">Intercepting Filter</a> pattern
 * to give a user full control over how an event is handled and how the {@link ChannelHandler}s in a pipeline
 * interact with each other.
 * <p>
 * {@link ChannelHandler}列表,用于处理或拦截{@link Channel}的入站事件和出站操作.
 * {@link ChannelPipeline}实现了
 * <a href="http://www.oracle.com/technetwork/java/interceptingfilter-142169.html">拦截过滤器</a>模式的一种高级形式,
 * 使用户能够完全控制如何处理事件,以及管道中的{@link ChannelHandler}如何相互交互.
 *
 *
 * <h3>创建pipeline</h3>
 * <p>
 * 每个channel都拥有自己的pipeline,当一个新的channel被创建以后,pipeline会被自动创建.
 *
 * <h3>在pipeline中消息如何流动</h3>
 * <p>
 * The following diagram describes how I/O events are processed by {@link ChannelHandler}s in a {@link ChannelPipeline}
 * typically. An I/O event is handled by either a {@link ChannelInboundHandler} or a {@link ChannelOutboundHandler}
 * and be forwarded to its closest handler by calling the event propagation methods defined in
 * {@link ChannelHandlerContext}, such as {@link ChannelHandlerContext#fireChannelRead(Object)} and
 * {@link ChannelHandlerContext#write(Object)}.
 * <p>
 * 下图描述了一般情况下{@link ChannelPipeline}中的{@link ChannelHandler}如何处理I/O事件.
 * I/O事件由{@link ChannelInboundHandler}或{@link ChannelOutboundHandler}处理,
 * 并通过调用{@link ChannelHandlerContext}中定义的事件传播方法转发到其最近的handler,
 * 例如{@link ChannelHandlerContext#fireChannelRead(Object)}和{@link ChannelHandlerContext#write(Object)}.
 *
 * <pre>
 *                                                 I/O Request
 *                                            via {@link Channel} or
 *                                        {@link ChannelHandlerContext}
 *                                                      |
 *  +---------------------------------------------------+---------------+
 *  |                           ChannelPipeline         |               |
 *  |                                                  \|/              |
 *  |    +---------------------+            +-----------+----------+    |
 *  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  .               |
 *  |               .                                   .               |
 *  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
 *  |        [ method call]                       [method call]         |
 *  |               .                                   .               |
 *  |               .                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  +---------------+-----------------------------------+---------------+
 *                  |                                  \|/
 *  +---------------+-----------------------------------+---------------+
 *  |               |                                   |               |
 *  |       [ Socket.read() ]                    [ Socket.write() ]     |
 *  |                                                                   |
 *  |  Netty Internal I/O Threads (Transport Implementation)            |
 *  +-------------------------------------------------------------------+
 * </pre>
 * An inbound event is handled by the inbound handlers in the bottom-up direction as shown on the left side of the
 * diagram.  An inbound handler usually handles the inbound data generated by the I/O thread on the bottom of the
 * diagram.  The inbound data is often read from a remote peer via the actual input operation such as
 * {@link SocketChannel#read(ByteBuffer)}.  If an inbound event goes beyond the top inbound handler, it is discarded
 * silently, or logged if it needs your attention.
 * <p>
 * 入站事件由自下而上的入站handler处理,如图左侧所示.入站handler通常处理由图底部的I/O线程生成的入站数据.
 * 通常通过实际输入操作(例如{@link SocketChannel#read(ByteBuffer)})从远程对等方读取入站数据.
 * 如果入站事件超出了顶部入站handler,则会被默默地将其丢弃,或者在需要你注意时进行记录.
 *
 *
 *
 * <p>
 * An outbound event is handled by the outbound handler in the top-down direction as shown on the right side of the
 * diagram.  An outbound handler usually generates or transforms the outbound traffic such as write requests.
 * If an outbound event goes beyond the bottom outbound handler, it is handled by an I/O thread associated with the
 * {@link Channel}. The I/O thread often performs the actual output operation such as
 * {@link SocketChannel#write(ByteBuffer)}.
 * <p>
 * 出站事件由自上而下的出站handler处理,如图右侧所示.出站处理程序通常会生成或转换出站流量,例如写入请求.
 * 如果出站事件超出底部出站handler,则它由与{@link Channel}关联的I/O线程处理.
 * I/O线程经常执行实际的输出操作,例如{@link SocketChannel#write(ByteBuffer)}.
 *
 * <p>
 * 比如,假设我们创建了下面的pipeline:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * 在上面的示例中,名字以{@code Inbound}开始意味着是一个入站handler,名字以{@code Outbound}开始意味着是一个出站handler.
 * <p>
 * In the given example configuration, the handler evaluation order is 1, 2, 3, 4, 5 when an event goes inbound.
 * When an event goes outbound, the order is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation of certain handlers to shorten the stack depth:
 * <p>
 * 在给定的示例配置中,当事件进入时,handler评估顺序为1,2,3,4,5.当事件出去时,评估顺序为5,4,3,2,1.
 * 基于这一原则,{@link ChannelPipeline}跳过对某些处理程序的评估,以缩短堆栈深度:
 *
 * <ul>
 * <li>
 * 3和4不实现{@link ChannelInboundHandler},因此,入站事件的实际评估顺序为:1,2和5.
 * </li>
 * <li>
 * <p>
 * 1和2不实现{@link ChannelOutboundHandler},因此实际的出站评估顺序为5,4和3.
 *
 * </li>
 * <li>如果5同时实现了{@link ChannelInboundHandler} and {@link ChannelOutboundHandler},
 * 因此入站和出站事件的评估顺序可分别为125和543.</li>
 * </ul>
 *
 * <h3>转发事件到下一个handler</h3>
 * <p>
 * 正如您在图中所示,处理程序必须在{@link ChannelHandlerContext}中调用事件传播方法,以将事件转发到其下一个处理程序.这些方法包括:
 * <ul>
 * <li>入站消息传播方法:
 * <ul>
 * <li>{@link ChannelHandlerContext#fireChannelRegistered()}</li>
 * <li>{@link ChannelHandlerContext#fireChannelActive()}</li>
 * <li>{@link ChannelHandlerContext#fireChannelRead(Object)}</li>
 * <li>{@link ChannelHandlerContext#fireChannelReadComplete()}</li>
 * <li>{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}</li>
 * <li>{@link ChannelHandlerContext#fireUserEventTriggered(Object)}</li>
 * <li>{@link ChannelHandlerContext#fireChannelWritabilityChanged()}</li>
 * <li>{@link ChannelHandlerContext#fireChannelInactive()}</li>
 * <li>{@link ChannelHandlerContext#fireChannelUnregistered()}</li>
 * </ul>
 * </li>
 * <li>出站消息传播方法:
 * <ul>
 * <li>{@link ChannelHandlerContext#bind(SocketAddress, ChannelPromise)}</li>
 * <li>{@link ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelPromise)}</li>
 * <li>{@link ChannelHandlerContext#write(Object, ChannelPromise)}</li>
 * <li>{@link ChannelHandlerContext#flush()}</li>
 * <li>{@link ChannelHandlerContext#read()}</li>
 * <li>{@link ChannelHandlerContext#disconnect(ChannelPromise)}</li>
 * <li>{@link ChannelHandlerContext#close(ChannelPromise)}</li>
 * <li>{@link ChannelHandlerContext#deregister(ChannelPromise)}</li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * 以下示例显示了通常如何完成事件传播:
 *
 * <pre>
 * public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         System.out.println("Connected!");
 *         ctx.fireChannelActive();
 *     }
 * }
 *
 * public class MyOutboundHandler extends {@link ChannelOutboundHandlerAdapter} {
 *     {@code @Override}
 *     public void close({@link ChannelHandlerContext} ctx, {@link ChannelPromise} promise) {
 *         System.out.println("Closing ..");
 *         ctx.close(promise);
 *     }
 * }
 * </pre>
 *
 * <h3>构建pipline</h3>
 * <p>
 *
 * 用户应该在pipeline中有一个或多个{@link ChannelHandler}来接收I/O消息(e.g:读)和请求I/O操作(e.g:写和关闭).
 *
 * For example, a typical server will have the following handlers
 * in each channel's pipeline, but your mileage may vary depending on the complexity and characteristics of the
 * protocol and business logic:
 *
 * 例如,一个典型的服务器在每个channel的pipeline中都有以下handler,但是您的里程可能会根据协议和业务逻辑的复杂性和特征而有所不同.
 * <ol>
 * <li>Protocol Decoder - 转换二进制数据(e.g. {@link ByteBuf})到一个Java对象.</li>
 * <li>Protocol Encoder - 转换一个Java对象到二进制数据.</li>
 * <li>Business Logic Handler - 执行实际的业务逻辑(e.g. 数据访问).</li>
 * </ol>
 * <p>
 *  它可以表示为以下示例中所示:
 *
 * <pre>
 * static final {@link EventExecutorGroup} group = new {@link DefaultEventExecutorGroup}(16);
 * ...
 *
 * {@link ChannelPipeline} pipeline = ch.pipeline();
 *
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 *
 * // 告诉pipeline在与I/O线程不同的线程中运行MyBusinessLogicHandler的事件handler方法
 * // 这样I/O线程就不会被耗时的任务阻塞.
 * //
 * // 如果你的业务逻辑是全异步或者完成的非常快,你不需要指定一个group
 * pipeline.addLast(group, "handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * 因为{@link ChannelPipeline}是线程安全的,所以{@link ChannelHandler}可以在任何时候被添加或者移除.
 * 例如,你可以在即将交换敏感信息时插入加密处理程序,并在交换后将其删除.
 */
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {

    /**
     * 在这个pipeline的第一个位置插入{@link ChannelHandler}.
     *
     * @param name    the name of the handler to insert first
     * @param handler the handler to insert first
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(String name, ChannelHandler handler);

    /**
     * 在这个pipeline的第一个位置插入{@link ChannelHandler}.
     *
     * @param group   the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                methods
     * @param name    the name of the handler to insert first
     * @param handler the handler to insert first
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * 在这个pipeline最后的位置追加一个{@link ChannelHandler}.
     *
     * @param name    the name of the handler to append
     * @param handler the handler to append
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    ChannelPipeline addLast(String name, ChannelHandler handler);

    /**
     * 在这个pipeline最后的位置追加一个{@link ChannelHandler}.
     *
     * @param group   the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                methods
     * @param name    the name of the handler to append
     * @param handler the handler to append
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert before
     * @param handler  the handler to insert before
     * @throws NoSuchElementException   if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert before
     * @param handler  the handler to insert before
     * @throws NoSuchElementException   if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert after
     * @param handler  the handler to insert after
     * @throws NoSuchElementException   if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert after
     * @param handler  the handler to insert after
     * @throws NoSuchElementException   if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param handlers the handlers to insert first
     */
    ChannelPipeline addFirst(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                 methods.
     * @param handlers the handlers to insert first
     */
    ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param handlers the handlers to insert last
     */
    ChannelPipeline addLast(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                 methods.
     * @param handlers the handlers to insert last
     */
    ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline.
     *
     * @param handler the {@link ChannelHandler} to remove
     * @throws NoSuchElementException if there's no such handler in this pipeline
     * @throws NullPointerException   if the specified handler is {@code null}
     */
    ChannelPipeline remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this pipeline.
     *
     * @param name the name under which the {@link ChannelHandler} was stored.
     * @return the removed handler
     * @throws NoSuchElementException if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException   if the specified name is {@code null}
     */
    ChannelHandler remove(String name);

    /**
     * Removes the {@link ChannelHandler} of the specified type from this pipeline.
     *
     * @param <T>         the type of the handler
     * @param handlerType the type of the handler
     * @return the removed handler
     * @throws NoSuchElementException if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException   if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     * @throws NoSuchElementException if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     * @throws NoSuchElementException if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in this pipeline.
     *
     * @param oldHandler the {@link ChannelHandler} to be replaced
     * @param newName    the name under which the replacement should be added
     * @param newHandler the {@link ChannelHandler} which is used as replacement
     * @return itself
     * @throws NoSuchElementException   if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException if a handler with the specified new name already exists in this
     *                                  pipeline, except for the handler to be replaced
     * @throws NullPointerException     if the specified old handler or new handler is
     *                                  {@code null}
     */
    ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new handler in this pipeline.
     *
     * @param oldName    the name of the {@link ChannelHandler} to be replaced
     * @param newName    the name under which the replacement should be added
     * @param newHandler the {@link ChannelHandler} which is used as replacement
     * @return the removed handler
     * @throws NoSuchElementException   if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException if a handler with the specified new name already exists in this
     *                                  pipeline, except for the handler to be replaced
     * @throws NullPointerException     if the specified old handler or new handler is
     *                                  {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new handler in this pipeline.
     *
     * @param oldHandlerType the type of the handler to be removed
     * @param newName        the name under which the replacement should be added
     * @param newHandler     the {@link ChannelHandler} which is used as replacement
     * @return the removed handler
     * @throws NoSuchElementException   if the handler of the specified old handler type does not exist
     *                                  in this pipeline
     * @throws IllegalArgumentException if a handler with the specified new name already exists in this
     *                                  pipeline, except for the handler to be replaced
     * @throws NullPointerException     if the specified old handler or new handler is
     *                                  {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                         ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler first();

    /**
     * Returns the context of the first {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext firstContext();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler last();

    /**
     * Returns the context of the last {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext lastContext();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     *
     * @return the handler with the specified name.
     * {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     *
     * @return the handler of the specified handler type.
     * {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     *
     * @return the context object of the specified handler.
     * {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name.
     * {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     *
     * @return the context object of the handler of the specified type.
     * {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel channel();

    /**
     * Returns the {@link List} of the handler names.
     */
    List<String> names();

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     * <p>
     * 转换这个pipeline到一个有序的{@link Map},key是handler的名字,value是具体的handler实例
     */
    Map<String, ChannelHandler> toMap();

    @Override
    ChannelPipeline fireChannelRegistered();

    @Override
    ChannelPipeline fireChannelUnregistered();

    @Override
    ChannelPipeline fireChannelActive();

    @Override
    ChannelPipeline fireChannelInactive();

    @Override
    ChannelPipeline fireExceptionCaught(Throwable cause);

    @Override
    ChannelPipeline fireUserEventTriggered(Object event);

    @Override
    ChannelPipeline fireChannelRead(Object msg);

    @Override
    ChannelPipeline fireChannelReadComplete();

    @Override
    ChannelPipeline fireChannelWritabilityChanged();

    @Override
    ChannelPipeline flush();
}
