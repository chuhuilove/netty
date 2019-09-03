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

import io.netty.bootstrap.Bootstrap;
import io.netty.util.concurrent.BlockingOperationException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;


/**
 * The result of an asynchronous {@link Channel} I/O operation.
 * 异步{@link Channel}I/O操作的结果.
 *
 * <p>
 *
 * 在Netty中,所有I/O操作都是异步的.这意味着任何I/O调用都将立即返回,并且不保证在调用结束时已经请求的I/O操作已完成.
 * 相反,你将被返回一个{@link ChannelFuture}实例,该实例为您提供有关I/O操作的结果或状态的信息.
 *
 *
 * <p>
 * A {@link ChannelFuture} is either <em>uncompleted</em> or <em>completed</em>.
 * When an I/O operation begins, a new future object is created.  The new future
 * is uncompleted initially - it is neither succeeded, failed, nor cancelled
 * because the I/O operation is not finished yet.  If the I/O operation is
 * finished either successfully, with failure, or by cancellation, the future is
 * marked as completed with more specific information, such as the cause of the
 * failure.  Please note that even failure and cancellation belong to the
 * completed state.
 *
 * 当I/O操作开始,则一个新的future对象被创建.新的future最初是未完成的-它既不是已成功或已失败,也不是已取消的,因为I/O操作还没有完成.
 * 如果I/O操作成功完成,失败或取消,则将使用更具体的信息(e.g 失败原因)将future标记为已完成.
 * 请注意,每个失败和取消属于完成状态.
 *
 *
 *
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 * Various methods are provided to let you check if the I/O operation has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add {@link ChannelFutureListener}s so you
 * can get notified when the I/O operation is completed.
 *
 * 提供了各种方法来检查I/O操作是否已完成，等待完成，并检索I/O操作的结果.
 * 它还允许你添加{@link ChannelFutureListener},以便在I/O操作完成时收到通知.
 *
 *
 * <h3>Prefer {@link #addListener(GenericFutureListener)} to {@link #await()}</h3>
 *
 * It is recommended to prefer {@link #addListener(GenericFutureListener)} to
 *  * {@link #await()} wherever possible to get notified when an I/O operation is
 *  * done and to do any follow-up tasks.
 *
 * 建议尽可能选择{@link #addListener(GenericFutureListener)}到{@link #await()},以便在完成I/O操作时收到通知并执行其他后续任务.
 *
 * <p>
 * {@link #addListener(GenericFutureListener)} is non-blocking.  It simply adds
 * the specified {@link ChannelFutureListener} to the {@link ChannelFuture}, and
 * I/O thread will notify the listeners when the I/O operation associated with
 * the future is done.  {@link ChannelFutureListener} yields the best
 * performance and resource utilization because it does not block at all, but
 * it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 *
 * {@link #addListener(GenericFutureListener)}是非阻塞的.
 * 它只是将指定的{@link ChannelFutureListener}添加到{@link ChannelFuture},I/O线程将在完成与future相关的I/O操作时通知侦听器.
 *{@link ChannelFutureListener}产生最佳性能和资源利用率,因为它根本不会阻塞,但如果你不习惯事件驱动编程,那么实现顺序逻辑可能会很棘手.
 *
 * <p>
 * By contrast, {@link #await()} is a blocking operation.  Once called, the
 * caller thread blocks until the operation is done.  It is easier to implement
 * a sequential logic with {@link #await()}, but the caller thread blocks
 * unnecessarily until the I/O operation is done and there's relatively
 * expensive cost of inter-thread notification.  Moreover, there's a chance of
 * dead lock in a particular circumstance, which is described below.
 *
 * 相比之下,{@link #await()} 是阻塞操作.一旦被调用,调用者线程会被阻塞直到操作结束.
 * 使用{@link #await()}实现顺序逻辑更容易,但调用者程序线程在I/O操作完成之前不必要地阻塞,并且线程间通知的成本相对较高.
 * 此外，在特定情况下存在死锁的可能性,如下所述.
 *
 *
 * <h3>不要在{@link ChannelHandler}中调用{@link #await()}</h3>
 * <p>
 * The event handler methods in {@link ChannelHandler} are usually called by
 * an I/O thread.  If {@link #await()} is called by an event handler
 * method, which is called by the I/O thread, the I/O operation it is waiting
 * for might never complete because {@link #await()} can block the I/O
 * operation it is waiting for, which is a dead lock.
 *
 * 事件handler方法在{@link ChannelHandler}中通常是被I/O线程调用的.
 * 如果由I/O线程调用的事件handler方法调用{@link #await()},则它正在等待的I/O操作可能永远不会完成,
 * 因为{@link #await()}可以阻止它正在等待的I/O操作,这是一个死锁.
 *
 * <pre>
 * // BAD - 永远不要这样做
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.addListener(new {@link ChannelFutureListener}() {
 *         public void operationComplete({@link ChannelFuture} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread.  Otherwise,
 * {@link BlockingOperationException} will be raised to prevent a dead lock.
 *
 * 尽管存在上述缺点，但肯定会有更方便的方式来调用{@link #await()}.
 * 在这种情况下,请确保不要在I/O线程中调用{@link #await()}.
 * 否则,将引发{@link BlockingOperationException}以防止死锁.
 *
 *
 * <h3>不要混淆I/O timeout和await timeout</h3>
 *
 * 使用{@link #await(long)},{@link #await(long, TimeUnit)},{@link #awaitUninterruptibly(long)},
 * 或{@link #awaitUninterruptibly(long, TimeUnit)}指定的超时值 完全与I/O超时无关.
 * 如果I/O操作超时,future将被标记为'已完成但失败(completed with failure)',如上图所示.例如,应通过特定于传输的选项配置连接超时
 *
 * <pre>
 * // BAD - 永远不要这样做
 * {@link Bootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // 用户取消连接尝试
 * } else if (!f.isSuccess()) {
 *     // 这里你可以获取到一个NullPointerException,
 *     // 因为future可能还没有完成.
 *     f.cause().printStackTrace();
 * } else {
 *     // 连接成功建立
 * }
 *
 * // GOOD
 * {@link Bootstrap} b = ...;
 * // 配置连接超时选项.
 * <b>b.option({@link ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // 现在我们确定future已经完成.
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // 用户取消连接尝试
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // 连接成功建立
 * }
 * </pre>
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     */
    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    /**
     * Returns {@code true} if this {@link ChannelFuture} is a void future and so not allow to call any of the
     * following methods:
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     */
    boolean isVoid();
}
